from __future__ import annotations

# Adapted from Zheng-Chong/CatVTON model/attn_processor.py
from torch.nn import functional as F
import torch

# MPS'te O(N^2) attention tamponunu sinirlamak icin query-seq dilimleme.
# 0 = kapali; 512/1024 tipik guvenli degerler.
_ATTN_SLICE_SIZE = 512


def set_attn_slice_size(size: int) -> None:
    global _ATTN_SLICE_SIZE
    _ATTN_SLICE_SIZE = max(0, int(size))


def get_attn_slice_size() -> int:
    return _ATTN_SLICE_SIZE


class SkipAttnProcessor(torch.nn.Module):
    def __init__(self, *args, **kwargs) -> None:
        super().__init__()

    def __call__(
        self,
        attn,
        hidden_states,
        encoder_hidden_states=None,
        attention_mask=None,
        temb=None,
    ):
        return hidden_states


class AttnProcessor2_0(torch.nn.Module):
    def __init__(self, hidden_size=None, cross_attention_dim=None, **kwargs):
        super().__init__()
        if not hasattr(F, "scaled_dot_product_attention"):
            raise ImportError("AttnProcessor2_0 requires PyTorch 2.0+")

    def __call__(
        self,
        attn,
        hidden_states,
        encoder_hidden_states=None,
        attention_mask=None,
        temb=None,
        *args,
        **kwargs,
    ):
        residual = hidden_states

        if attn.spatial_norm is not None:
            hidden_states = attn.spatial_norm(hidden_states, temb)

        input_ndim = hidden_states.ndim
        if input_ndim == 4:
            batch_size, channel, height, width = hidden_states.shape
            hidden_states = hidden_states.view(batch_size, channel, height * width).transpose(1, 2)

        batch_size, sequence_length, _ = (
            hidden_states.shape if encoder_hidden_states is None else encoder_hidden_states.shape
        )

        if attention_mask is not None:
            attention_mask = attn.prepare_attention_mask(attention_mask, sequence_length, batch_size)
            attention_mask = attention_mask.view(batch_size, attn.heads, -1, attention_mask.shape[-1])

        if attn.group_norm is not None:
            hidden_states = attn.group_norm(hidden_states.transpose(1, 2)).transpose(1, 2)

        query = attn.to_q(hidden_states)
        if encoder_hidden_states is None:
            encoder_hidden_states = hidden_states
        elif attn.norm_cross:
            encoder_hidden_states = attn.norm_encoder_hidden_states(encoder_hidden_states)

        key = attn.to_k(encoder_hidden_states)
        value = attn.to_v(encoder_hidden_states)

        inner_dim = key.shape[-1]
        head_dim = inner_dim // attn.heads

        query = query.view(batch_size, -1, attn.heads, head_dim).transpose(1, 2)
        key = key.view(batch_size, -1, attn.heads, head_dim).transpose(1, 2)
        value = value.view(batch_size, -1, attn.heads, head_dim).transpose(1, 2)

        slice_size = get_attn_slice_size()
        seq_q = query.shape[2]
        if slice_size and seq_q > slice_size:
            chunks = []
            for start in range(0, seq_q, slice_size):
                end = min(start + slice_size, seq_q)
                q_slice = query[:, :, start:end, :]
                mask_slice = None
                if attention_mask is not None:
                    mask_slice = attention_mask[:, :, start:end, :]
                chunks.append(
                    F.scaled_dot_product_attention(
                        q_slice,
                        key,
                        value,
                        attn_mask=mask_slice,
                        dropout_p=0.0,
                        is_causal=False,
                    )
                )
            hidden_states = torch.cat(chunks, dim=2)
        else:
            hidden_states = F.scaled_dot_product_attention(
                query, key, value, attn_mask=attention_mask, dropout_p=0.0, is_causal=False
            )
        hidden_states = hidden_states.transpose(1, 2).reshape(batch_size, -1, attn.heads * head_dim)
        hidden_states = hidden_states.to(query.dtype)
        hidden_states = attn.to_out[0](hidden_states)
        hidden_states = attn.to_out[1](hidden_states)

        if input_ndim == 4:
            hidden_states = hidden_states.transpose(-1, -2).reshape(batch_size, channel, height, width)

        if attn.residual_connection:
            hidden_states = hidden_states + residual

        return hidden_states / attn.rescale_output_factor
