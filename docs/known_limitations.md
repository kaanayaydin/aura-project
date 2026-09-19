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
