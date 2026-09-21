# Bilinen sınırlar

## Perspektif distorsiyonu (deskew kapsamı dışı)

`askisiz_hafif_saga` (ve benzeri yerde yatan, kameraya doğru kısalmış tişörtler)
görsel olarak “hafif yatık” durur. Deskew adımı **çalışır**; `minAreaRect` /
PCA’nın ölçtüğü düzlem-içi açı ~0° çıkar ve `AURA_STUDIO_DESKEW_MIN_ABS_DEG`
(varsayılan 0.5°) eşiğinin altında kaldığı için rotasyon uygulanmaz
(`deskew_skip_reason=angle_below_min_threshold`).

Yatıklık hissi kamera perspektifinden gelir, silüetin eksen dönmesinden değil.
Perspektif düzeltme (homografi / vanishing-point) bu sürümün kapsamı dışındadır.

Ayırt etmek için `decision.json`:

- `deskew_step_executed`
- `deskew_input_angle_estimated`
- `deskew_angle_applied`
- `deskew_skip_reason`
- `deskew_min_abs_deg`

## Boş sahne → CLIP yanlış-pozitifi (kategori fallback)

YOLO 0 tespit olunca full-frame chroma+CLIP çalışır. Eski `refine_garment_alpha` boş maskeye **merkez elips** uyduruyordu (`mean_opaque≈0.38`). Bu değer `0.06–0.80` kapısının içinde kaldığı için `empty_mask` / `cutout_failed` tetiklenmiyor, CLIP duvar dokusunu `dress` (~0.4–0.6) sanıyordu.

**Düzeltildi (düz/gürültülü duvar, ahşap zemin):** boş maskede elips uydurulmaz → `empty_mask` veya `cutout_failed`. Golden: `empty_wall_flat`, `empty_wall_noise`, `empty_wood_floor`.

**Known failure:** `empty_gradient` — polarite ters çevirince çarşaf-benzeri maske (`mean_opaque≈0.68`). `0.80` eşiği düşürülemez çünkü gerçek tişört `askisiz_perspektif_golge` mean≈0.617. CLIP bu vakada `below_threshold` veya `dress` FP üretebilir; geometrik kapı hâlâ kıyafet sanır (`tests/golden_set/category/expected.json`).
