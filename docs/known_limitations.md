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

## Kıyafet-dışı yapılı nesne → CLIP yanlış-pozitifi

B2 boş sahne (duvar/zemin, kesilecek nesne yok) ayrı sınıftır. Burada nesne **var** ve chroma keser; 9 etiketli CLIP (negatif/"boş" sınıf yok, `clip_min_confidence=0.0`) en yakın kıyafet etiketini verir.

Bu koşudaki skorlar (chroma, rembg kapalı; dağılım doğrulanmadı, tek sentetik PNG'ler):

| vaka | top1 | top2 | gap | sonuç |
|---|---|---|---|---|
| `nongarment_red_circle` | shirt 0.3711 | dress 0.2911 | 0.0800 | FP shirt |
| `nongarment_blue_box` | pants 0.4028 | shirt 0.1611 | 0.2417 | FP pants |
| `nongarment_mug` | pants 0.3755 | glasses 0.1487 | 0.2268 | FP pants |
| `nongarment_yellow_triangle` | shirt 0.2944 | t-shirt 0.2439 | 0.0505 | `below_threshold` (0.30) |

Aynı koşuda gerçek kıyafet kardeş-etiket boşluğu:

| vaka | top1 | top2 | gap |
|---|---|---|---|
| `yolo_empty_borderline_fg` (shirt) | 0.4050 | t-shirt 0.3490 | 0.0560 |
| `yolo_empty_perspective_shadow_tee` | shirt 0.4820 | t-shirt 0.4533 | 0.0287 |
| `yolo_empty_pants` | pants 0.7408 | t-shirt 0.1026 | 0.6382 |

**Neden top1−top2 gap reddi bu turda uygulanmadı:** kırmızı daireyi yakalayan bir eşik (`gap<0.10`) gerçek `shirt`/`t-shirt` kardeşlerini de reddeder. Mavi kutu / kupa ise yanlış ama **kararlı** (gap ~0.23) — gap onları kaçırır. Golden: üç FP `known_failure`; üçgen beklenen `below_threshold`.

## Küçük giysi vs leftover (mean 0.02–0.06)

`has_meaningful_alpha` eski `opaque>=0.05` kapısı kare alanının %3–5’ini dolduran meşru giysiyi `cutout_failed` yapıyordu. `unusable_mask_reason` ayrıca `mean_opaque<0.06` iken hepsini `cutout_failed` sayıyordu.

Bu koşuda (chroma, tek sentetik PNG; dağılım yok):

| vaka | mean | fg-bg L1 | sonuç |
|---|---|---|---|
| `small_garment_3pct` | 0.0235 | 366 | kabul, CLIP shirt 0.655 |
| `small_garment_5pct` | 0.0417 | 366 | kabul, CLIP shirt 0.437 |
| `tiny_garment_distant` | 0.0104 | yüksek | `garment_too_small` |
| `yolo_empty_blank_scene` | 0.0405 | 90 | `cutout_failed` (leftover) |
| `empty_wood_floor` | 0.0033 | 20 | `cutout_failed` |

Eşikler `_SMALL_ACCEPT_MEAN=0.022` ve `_FG_BG_L1_MIN=150` yalnız bu tabloya göre seçildi. Açık renkli giysi + açık zemin (L1<150) hâlâ `cutout_failed` kalabilir.
