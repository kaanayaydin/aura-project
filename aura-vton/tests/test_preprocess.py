"""768x1024 CatVTON preprocess testleri."""

from PIL import Image

from app.services.catvton.preprocess import (
    CATVTON_HEIGHT,
    CATVTON_WIDTH,
    ensure_triplet,
    prepare_garment,
    prepare_mask,
    prepare_person,
    restore_original_size,
)


def test_prepare_person_forces_768x1024():
    person = Image.new("RGB", (1016, 1694), color=(20, 30, 40))
    aligned, original = prepare_person(person, (CATVTON_WIDTH, CATVTON_HEIGHT))
    assert original == (1016, 1694)
    assert aligned.size == (768, 1024)


def test_prepare_garment_and_mask_match_person():
    person = Image.new("RGB", (900, 1600), color=(10, 10, 10))
    garment = Image.new("RGB", (400, 500), color=(200, 50, 50))
    mask = Image.new("L", (900, 1600), color=255)

    person_a, _ = prepare_person(person)
    garment_a = prepare_garment(garment, person_a.size)
    # Maske person ile ayni orijinal boyuttan hizalanmali
    mask_a = prepare_mask(mask, person_a.size)

    person_a, garment_a, mask_a = ensure_triplet(person_a, garment_a, mask_a)
    assert person_a.size == garment_a.size == mask_a.size == (768, 1024)


def test_restore_original_size():
    out = Image.new("RGB", (768, 1024), color=(1, 2, 3))
    restored = restore_original_size(out, (1016, 1694))
    assert restored.size == (1016, 1694)
