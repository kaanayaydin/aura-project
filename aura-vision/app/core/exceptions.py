"""Servis katmaninin paylasilan hata tipleri."""


class ModelUnavailableError(RuntimeError):
    """Bir AI modeli yuklenemedi (indirme hatasi, bozuk dosya, yetersiz bellek vb.)."""
