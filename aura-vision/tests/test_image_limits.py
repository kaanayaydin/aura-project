"""Header-only piksel limiti — decode yok, Java ImageForeground ile senkron."""

from __future__ import annotations

import struct
import zlib

import pytest

from app.services.image_limits import (
    MAX_PIXELS,
    MAX_SIDE,
    ImagePixelLimitError,
    ImageUnreadableError,
    assert_within_pixel_limits,
    peek_image_size,
)


def _png_declared(width: int, height: int) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00"))
        + chunk(b"IEND", b"")
    )


def test_png_bomb_rejected_from_header_without_load():
    bomb = _png_declared(30_000, 30_000)
    assert len(bomb) < 2_000
    width, height = peek_image_size(bomb)
    assert (width, height) == (30_000, 30_000)
    with pytest.raises(ImagePixelLimitError) as exc:
        assert_within_pixel_limits(bomb)
    assert exc.value.rejected_reason == "image_too_large"
    assert exc.value.width == 30_000


def test_unparseable_fail_closed():
    garbage = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"
    with pytest.raises(ImageUnreadableError) as exc:
        peek_image_size(garbage)
    assert exc.value.rejected_reason == "decode_failed"


def test_forty_eight_mp_conscious_reject():
    assert 8000 * 6000 > MAX_PIXELS
    with pytest.raises(ImagePixelLimitError):
        assert_within_pixel_limits(_png_declared(8000, 6000))


def test_phone_12mp_header_allowed():
    raw = _png_declared(4032, 3024)
    assert len(raw) < 2_000
    assert_within_pixel_limits(raw)


def test_max_side_reject():
    assert MAX_SIDE == 8192
    with pytest.raises(ImagePixelLimitError):
        assert_within_pixel_limits(_png_declared(8193, 100))
