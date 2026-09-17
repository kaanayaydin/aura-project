from __future__ import annotations

# Adapted from Zheng-Chong/CatVTON model/pipeline.py
import inspect
import logging
import os
from typing import Union

import PIL.Image
import torch
import tqdm
from accelerate import load_checkpoint_in_model
from diffusers import AutoencoderKL, DDIMScheduler, UNet2DConditionModel
from diffusers.utils.torch_utils import randn_tensor
from huggingface_hub import snapshot_download

from app.services.catvton.attn_processor import SkipAttnProcessor
from app.services.catvton.image_ops import (
    compute_vae_encodings,
    get_trainable_module,
    init_adapter,
    numpy_to_pil,
    prepare_image,
    prepare_mask_image,
    resize_and_crop,
    resize_and_padding,
)
from app.config import settings

logger = logging.getLogger("aura.vton.catvton")


class CatVTONPipeline:
    def __init__(
        self,
        base_ckpt: str,
        attn_ckpt: str,
        attn_ckpt_version: str = "mix",
        weight_dtype=torch.float32,
        device: str = "cpu",
        skip_safety_check: bool = True,
        use_tf32: bool = False,
        cache_dir: str | None = None,
    ):
        self.device = device
        self.weight_dtype = weight_dtype
        self.skip_safety_check = skip_safety_check
        self.cache_dir = cache_dir

        logger.info("CatVTON base model yukleniyor: %s → %s", base_ckpt, device)
        self.noise_scheduler = DDIMScheduler.from_pretrained(
            base_ckpt, subfolder="scheduler", cache_dir=cache_dir
        )
        self.vae = AutoencoderKL.from_pretrained(
            "stabilityai/sd-vae-ft-mse", cache_dir=cache_dir
        ).to(device, dtype=weight_dtype)
        self.unet = UNet2DConditionModel.from_pretrained(
            base_ckpt, subfolder="unet", cache_dir=cache_dir
        ).to(device, dtype=weight_dtype)
        init_adapter(self.unet, cross_attn_cls=SkipAttnProcessor)
        self.attn_modules = get_trainable_module(self.unet, "attention")
        self.auto_attn_ckpt_load(attn_ckpt, attn_ckpt_version)
        self.enable_memory_optimizations()

        if use_tf32 and device.startswith("cuda"):
            torch.set_float32_matmul_precision("high")
            torch.backends.cuda.matmul.allow_tf32 = True

    def enable_memory_optimizations(self) -> None:
        """VAE slicing + dilimli self-attn — MPS 18GiB buffer hatasini onler."""
        from app.services.catvton.attn_processor import set_attn_slice_size

        slice_size = int(settings.attn_slice_size)
        set_attn_slice_size(slice_size)
        try:
            if hasattr(self.vae, "enable_slicing"):
                self.vae.enable_slicing()
            if hasattr(self.vae, "enable_tiling"):
                self.vae.enable_tiling()
            logger.info(
                "CatVTON bellek opt: vae_slicing=on attn_slice=%s device=%s",
                slice_size,
                self.device,
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("VAE slicing acilamadi: %s", exc)

    def auto_attn_ckpt_load(self, attn_ckpt: str, version: str) -> None:
        sub_folder = {
            "mix": "mix-48k-1024",
            "vitonhd": "vitonhd-16k-512",
            "dresscode": "dresscode-16k-512",
        }[version]
        if os.path.exists(attn_ckpt):
            attention_path = os.path.join(attn_ckpt, sub_folder, "attention")
        else:
            repo_path = snapshot_download(repo_id=attn_ckpt, cache_dir=self.cache_dir)
            logger.info("CatVTON attn indirildi: %s → %s", attn_ckpt, repo_path)
            attention_path = os.path.join(repo_path, sub_folder, "attention")
        load_checkpoint_in_model(self.attn_modules, attention_path)

    def check_inputs(self, image, condition_image, mask, width, height):
        if (
            isinstance(image, torch.Tensor)
            and isinstance(condition_image, torch.Tensor)
            and isinstance(mask, torch.Tensor)
        ):
            return image, condition_image, mask
        # Zorunlu 768x1024 (veya ayarli W/H) — boyut uyumsuzlugunda once hizala
        target = (width, height)
        if image.size != mask.size:
            from app.services.catvton.preprocess import prepare_mask

            mask = prepare_mask(mask, target)
        if image.size != target:
            image = resize_and_crop(image, target)
        if mask.size != target:
            from app.services.catvton.preprocess import prepare_mask

            mask = prepare_mask(mask, target)
        if condition_image.size != target:
            condition_image = resize_and_padding(condition_image, target)
        assert image.size == mask.size == target, (
            f"Image/mask size mismatch after resize: image={image.size} mask={mask.size} target={target}"
        )
        assert condition_image.size == target, (
            f"Condition size mismatch: {condition_image.size} != {target}"
        )
        return image, condition_image, mask

    def prepare_extra_step_kwargs(self, generator, eta):
        extra_step_kwargs = {}
        if "eta" in set(inspect.signature(self.noise_scheduler.step).parameters.keys()):
            extra_step_kwargs["eta"] = eta
        if "generator" in set(inspect.signature(self.noise_scheduler.step).parameters.keys()):
            extra_step_kwargs["generator"] = generator
        return extra_step_kwargs

    @torch.no_grad()
    def __call__(
        self,
        image: Union[PIL.Image.Image, torch.Tensor],
        condition_image: Union[PIL.Image.Image, torch.Tensor],
        mask: Union[PIL.Image.Image, torch.Tensor],
        num_inference_steps: int = 50,
        guidance_scale: float = 2.5,
        height: int = 1024,
        width: int = 768,
        generator=None,
        eta: float = 1.0,
        **kwargs,
    ):
        concat_dim = -2
        image, condition_image, mask = self.check_inputs(image, condition_image, mask, width, height)
        image = prepare_image(image).to(self.device, dtype=self.weight_dtype)
        condition_image = prepare_image(condition_image).to(self.device, dtype=self.weight_dtype)
        mask = prepare_mask_image(mask).to(self.device, dtype=self.weight_dtype)
        masked_image = image * (mask < 0.5)

        masked_latent = compute_vae_encodings(masked_image, self.vae)
        condition_latent = compute_vae_encodings(condition_image, self.vae)
        mask_latent = torch.nn.functional.interpolate(
            mask, size=masked_latent.shape[-2:], mode="nearest"
        )
        del image, mask, condition_image

        masked_latent_concat = torch.cat([masked_latent, condition_latent], dim=concat_dim)
        mask_latent_concat = torch.cat([mask_latent, torch.zeros_like(mask_latent)], dim=concat_dim)

        latents = randn_tensor(
            masked_latent_concat.shape,
            generator=generator,
            device=masked_latent_concat.device,
            dtype=self.weight_dtype,
        )
        self.noise_scheduler.set_timesteps(num_inference_steps, device=self.device)
        timesteps = self.noise_scheduler.timesteps
        latents = latents * self.noise_scheduler.init_noise_sigma

        do_cfg = guidance_scale > 1.0
        if do_cfg:
            masked_latent_concat = torch.cat(
                [
                    torch.cat([masked_latent, torch.zeros_like(condition_latent)], dim=concat_dim),
                    masked_latent_concat,
                ]
            )
            mask_latent_concat = torch.cat([mask_latent_concat] * 2)

        extra_step_kwargs = self.prepare_extra_step_kwargs(generator, eta)
        num_warmup_steps = len(timesteps) - num_inference_steps * self.noise_scheduler.order
        with tqdm.tqdm(total=num_inference_steps) as progress_bar:
            for i, t in enumerate(timesteps):
                latent_model_input = torch.cat([latents] * 2) if do_cfg else latents
                latent_model_input = self.noise_scheduler.scale_model_input(latent_model_input, t)
                inpainting_input = torch.cat(
                    [latent_model_input, mask_latent_concat, masked_latent_concat], dim=1
                )
                noise_pred = self.unet(
                    inpainting_input,
                    t.to(self.device),
                    encoder_hidden_states=None,
                    return_dict=False,
                )[0]
                if do_cfg:
                    noise_pred_uncond, noise_pred_text = noise_pred.chunk(2)
                    noise_pred = noise_pred_uncond + guidance_scale * (
                        noise_pred_text - noise_pred_uncond
                    )
                latents = self.noise_scheduler.step(
                    noise_pred, t, latents, **extra_step_kwargs
                ).prev_sample
                if i == len(timesteps) - 1 or (
                    (i + 1) > num_warmup_steps and (i + 1) % self.noise_scheduler.order == 0
                ):
                    progress_bar.update()

        latents = latents.split(latents.shape[concat_dim] // 2, dim=concat_dim)[0]
        latents = 1 / self.vae.config.scaling_factor * latents
        image = self.vae.decode(latents.to(self.device, dtype=self.weight_dtype)).sample
        image = (image / 2 + 0.5).clamp(0, 1)
        image = image.cpu().permute(0, 2, 3, 1).float().numpy()
        return numpy_to_pil(image)
