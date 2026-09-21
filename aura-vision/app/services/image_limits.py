"""Piksel / kenar limitleri — Java ``ImageForeground`` ile senkron.

Ürün kararı (Faz 1b önkoşulu): 48MP telefon karesi (ör. 8000×6000)
BİLİNÇLİ reddedilir. Denetçi ölçümü: yasal 24MP JPEG Vision pipeline'da
~4.7GB tepe RAM; 144MP ~6.5GB / 60sn. Limiti 36MP'ye çekmek DoS yüzeyini
büyütür. 12–16MP (4032×3024) geçer.

Java: ``aura-backend/.../ImageForeground.MAX_PIXELS`` = 24_000_000,
``MAX_SIDE`` = 8192. Bu dosyayı değiştirince Java sabitlerini de güncelle.
"""

from __future__ import annotations

from PIL import Image, ImageFile

# ImageForeground.java ile birebir.
MAX_PIXELS = 24_000_000
MAX_SIDE = 8192


class ImagePixelLimitError(ValueError):
    rejected_reason = "image_too_large"

    def __init__(self, width: int, height: int):
        self.width = width
        self.height = height
        super().__init__(
            "Gorsel cozunurlugu cok buyuk ({0}x{1}, en fazla {2} MP).".format(
                width, height, MAX_PIXELS // 1_000_000
            )
        )


class ImageUnreadableError(ValueError):
    """Header parse edilemedi — fail-closed (BMP/WebP eklentisiz vs.)."""

    rejected_reason = "decode_failed"

    def __init__(self, detail: str = "Gorsel header okunamadi"):
        super().__init__(detail)


def peek_image_size(data: bytes) -> tuple[int, int]:
    """Pillow ImageFile.Parser — IHDR/SOF gelince size; tam decode yok.

    Parser.image set olunca durur; close()/load() çağrılmaz.
    Pillow'un kendi MAX_IMAGE_PIXELS (~178MP) bombası 30k×30k IHDR'da
    peek'i keser; geçici olarak kapatılır, asıl limit MAX_PIXELS/MAX_SIDE.
    """
    if not data:
        raise ImageUnreadableError("Bos gorsel")
    previous = Image.MAX_IMAGE_PIXELS
    Image.MAX_IMAGE_PIXELS = None
    try:
        parser = ImageFile.Parser()
        step = 8192
        for offset in range(0, len(data), step):
            parser.feed(data[offset : offset + step])
            if parser.image is not None:
                width, height = parser.image.size
                if width <= 0 or height <= 0:
                    raise ImageUnreadableError("Gecersiz boyut")
                return int(width), int(height)
        raise ImageUnreadableError("Tanimlanamayan veya parse edilemeyen gorsel formati")
    except ImageUnreadableError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise ImageUnreadableError("Tanimlanamayan veya parse edilemeyen gorsel formati") from exc
    finally:
        Image.MAX_IMAGE_PIXELS = previous


def exceeds_limit(width: int, height: int) -> bool:
    if width > MAX_SIDE or height > MAX_SIDE:
        return True
    return int(width) * int(height) > MAX_PIXELS


def assert_within_pixel_limits(data: bytes) -> tuple[int, int]:
    """Decode etmeden reddet. Dönüş: (width, height)."""
    width, height = peek_image_size(data)
    if exceeds_limit(width, height):
        raise ImagePixelLimitError(width, height)
    return width, height
