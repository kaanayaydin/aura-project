from __future__ import annotations

# Adapted from Zheng-Chong/CatVTON model/utils.py + utils.py (inference helpers)
import numpy as np
import torch
from PIL import Image

from app.services.catvton.attn_processor import AttnProcessor2_0, SkipAttnProcessor


def init_adapter(unet, cross_attn_cls=SkipAttnProcessor, self_attn_cls=None, cross_attn_dim=None, **kwargs):
    if cross_attn_dim is None:
        cross_attn_dim = unet.config.cross_attention_dim
    attn_procs = {}
    for name in unet.attn_processors.keys():
        cross_attention_dim = None if name.endswith("attn1.processor") else cross_attn_dim
        if name.startswith("mid_block"):
            hidden_size = unet.config.block_out_channels[-1]
        elif name.startswith("up_blocks"):
            block_id = int(name[len("up_blocks.")])
            hidden_size = list(reversed(unet.config.block_out_channels))[block_id]
        elif name.startswith("down_blocks"):
            block_id = int(name[len("down_blocks.")])
            hidden_size = unet.config.block_out_channels[block_id]
        else:
            hidden_size = unet.config.block_out_channels[0]
        if cross_attention_dim is None:
            if self_attn_cls is not None:
                attn_procs[name] = self_attn_cls(
                    hidden_size=hidden_size, cross_attention_dim=cross_attention_dim, **kwargs
                )
            else:
                attn_procs[name] = AttnProcessor2_0(
                    hidden_size=hidden_size, cross_attention_dim=cross_attention_dim, **kwargs
                )
        else:
            attn_procs[name] = cross_attn_cls(
                hidden_size=hidden_size, cross_attention_dim=cross_attention_dim, **kwargs
            )
    unet.set_attn_processor(attn_procs)
    return torch.nn.ModuleList(unet.attn_processors.values())


def get_trainable_module(unet, trainable_module_name: str):
    if trainable_module_name != "attention":
        raise ValueError(f"Unsupported trainable_module_name: {trainable_module_name}")
    attn_blocks = torch.nn.ModuleList()
    for name, module in unet.named_modules():
        if "attn1" in name:
            attn_blocks.append(module)
    return attn_blocks


def compute_vae_encodings(image: torch.Tensor, vae: torch.nn.Module) -> torch.Tensor:
    pixel_values = image.to(memory_format=torch.contiguous_format).float()
    pixel_values = pixel_values.to(vae.device, dtype=vae.dtype)
    with torch.no_grad():
        model_input = vae.encode(pixel_values).latent_dist.sample()
    return model_input * vae.config.scaling_factor


def prepare_image(image):
    if isinstance(image, torch.Tensor):
        if image.ndim == 3:
            image = image.unsqueeze(0)
        return image.to(dtype=torch.float32)
    if isinstance(image, (Image.Image, np.ndarray)):
        image = [image]
    if isinstance(image, list) and isinstance(image[0], Image.Image):
        image = [np.array(i.convert("RGB"))[None, :] for i in image]
        image = np.concatenate(image, axis=0)
    elif isinstance(image, list) and isinstance(image[0], np.ndarray):
        image = np.concatenate([i[None, :] for i in image], axis=0)
    image = image.transpose(0, 3, 1, 2)
    return torch.from_numpy(image).to(dtype=torch.float32) / 127.5 - 1.0


def prepare_mask_image(mask_image):
    if isinstance(mask_image, torch.Tensor):
        if mask_image.ndim == 2:
            mask_image = mask_image.unsqueeze(0).unsqueeze(0)
        elif mask_image.ndim == 3 and mask_image.shape[0] == 1:
            mask_image = mask_image.unsqueeze(0)
        elif mask_image.ndim == 3 and mask_image.shape[0] != 1:
            mask_image = mask_image.unsqueeze(1)
        mask_image = mask_image.clone()
        mask_image[mask_image < 0.5] = 0
        mask_image[mask_image >= 0.5] = 1
        return mask_image

    if isinstance(mask_image, (Image.Image, np.ndarray)):
        mask_image = [mask_image]
    if isinstance(mask_image, list) and isinstance(mask_image[0], Image.Image):
        mask_image = np.concatenate(
            [np.array(m.convert("L"))[None, None, :] for m in mask_image], axis=0
        ).astype(np.float32) / 255.0
    elif isinstance(mask_image, list) and isinstance(mask_image[0], np.ndarray):
        mask_image = np.concatenate([m[None, None, :] for m in mask_image], axis=0)
    mask_image[mask_image < 0.5] = 0
    mask_image[mask_image >= 0.5] = 1
    return torch.from_numpy(mask_image)


def numpy_to_pil(images):
    if images.ndim == 3:
        images = images[None, ...]
    images = (images * 255).round().astype("uint8")
    if images.shape[-1] == 1:
        return [Image.fromarray(image.squeeze(), mode="L") for image in images]
    return [Image.fromarray(image) for image in images]


def resize_and_crop(image: Image.Image, size):
    w, h = image.size
    target_w, target_h = size
    if w / h < target_w / target_h:
        new_w, new_h = w, w * target_h // target_w
    else:
        new_h, new_w = h, h * target_w // target_h
    image = image.crop(((w - new_w) // 2, (h - new_h) // 2, (w + new_w) // 2, (h + new_h) // 2))
    return image.resize(size, Image.LANCZOS)


def resize_and_padding(image: Image.Image, size):
    w, h = image.size
    target_w, target_h = size
    if w / h < target_w / target_h:
        new_h = target_h
        new_w = w * target_h // h
    else:
        new_w = target_w
        new_h = h * target_w // w
    image = image.resize((new_w, new_h), Image.LANCZOS)
    padding = Image.new("RGB", size, (255, 255, 255))
    padding.paste(image, ((target_w - new_w) // 2, (target_h - new_h) // 2))
    return padding
