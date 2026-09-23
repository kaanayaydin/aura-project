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

| vaka | mean (normalize+polish) | fg-bg L1 | HTTP |
|---|---|---|---|
| `small_garment_3pct` | 0.02347 | 328.84 | 200 |
| `small_garment_5pct` | 0.04165 | 328.05 | 200 |
| `tiny_garment_distant` | 0.01033 | 329.70 | 422 `garment_too_small` |
| `yolo_empty_blank_scene` | 0.03773 | 122.81 | 422 `cutout_failed` |
| `empty_wood_floor` | 0.00312 | 10.98 | 422 `cutout_failed` |

Kaynak: `scripts/dump_normalize_garment_fixture_report.py` (skip_orientation yok, rembg=false). Analyzer `_cutout` (polish yok) `blank_scene` L1~90 veriyordu — canlı endpoint 122.81. Eşikler `SMALL_ACCEPT_MEAN=0.022` ve `FG_BG_L1_MIN=150` yalnız bu tabloya göre. `blank_scene` L1=122.81, 150→122 mutasyonunda kabul olur (HTTP 200); `tiny_garment` mean=0.01033 0.022→0.0105 penceresini kaçırır.

Mutasyon kilitleri (bu tur, sentetik RGBA): leftover L1∈(122,150)+mean≥0.022 → `cutout_failed` (`test_l1_window_122_150_stays_cutout_failed`); mean∈(0.0105,0.022)+L1≥150 → `garment_too_small` (`test_small_accept_window_0105_022_stays_too_small`). `tiny_garment_distant` mean=0.0104 ve `blank_scene` L1=90 bu pencereleri kaçırır.

## 48MP telefon karesi — bilinçli red

`ImageForeground.MAX_PIXELS = 24_000_000` ve `MAX_SIDE = 8192` (Python `image_limits.py` ile senkron). 8000×6000 (48MP) **bilinçli reddedilir**. Denetçi: yasal 24MP JPEG Vision pipeline’da ~4.7GB tepe RAM; 144MP ~6.5GB / 60sn. Limiti ~36MP’ye çekmek DoS yüzeyini büyütür. 12–16MP (4032×3024) geçer.

Header parse edilemeyen format (≥24 bayt, ImageIO + WebP/BMP magic yok) fail-closed: `decode_failed` / HTTP 422. 12 baytlık test sahte PNG bu eşiğin altında atlanır.

## Java `inspect()` OOM catch — ölü savunma ağı

`OutOfMemoryError` yakalanır ve `DECODE_FAILED` döner; header limiti + 512px altörnekleme sonrası yasal görseller `-Xmx24m`’de bile bu yola girmez. `outOfMemoryMappingIsDeadDefensiveNet` gerçek heap OOM tetiklemez, yalnızca eşlemeyi kilitler. Canlı OOM kanıtı değildir.

## VTON / Wardrobe object URL SSRF

`personImageUrl` ve wardrobe `imageUrl` yalnızca `aura.storage.endpoint` / `public-base-url` origin’ine ve `wardrobe|vton|avatars` bucket yol şablonuna izin verir. DNS: origin IP’leri `PIN_TTL=5dk` ile yenilenir; istekte host bir kez çözülür ve **TCP o IP’ye** açılır (hostname ile ikinci çözüm TOCTOU / rebinding). Host header ve TLS SNI orijinal hostname kalır. `127.0.0.1:8001` ve `169.254.169.254` allowlist dışı. Wardrobe indirme `PinnedHttpDownloader` (stream tavanı `maxImageBytes`, Content-Length önce). Worker `image_fetch` aynı pin + httplib SNI.

## Test edilmemiş mutasyon yüzeyi

Bu turda kapatılan: S2 (`item.getImageUrl()` kapısı — metadata giysi URL 403 + spy), S6 (stream tavanı gövdeyi belleğe almadan), S7 (imageUrl indirilemezse 201 ile URL kaydetme kaldırıldı), P3 (bağlantı pin’li IP), P4 (5dk pin yenileme), P6 (Host header hostname), P7 (Python IP rewrite + TOCTOU test), K8 (önceki tur, ≥24 garbage 422).

Açık bırakılan:

- **P8:** TLS SNI uygulandı (`SSLSocket` / `wrap_socket(server_hostname=…)`); canlı HTTPS sertifika ile rebinding kanıtı yok (testler HTTP).
- **VtonService.resolveResultBytes:** worker `resultImageUri` (`http://127.0.0.1:8001/outputs/…`) StorageUrlGuard dışı, `RestClient` gövdeyi sınırsız okur. Kullanıcı URL’si değil; worker origin ayrı allowlist değil.
- Origin hostname DNS’inin tamamen zehirlenmesi (pin refresh + istek çözümü aynı anda 169.254) — operasyonel CDN zehri, klasik rebinding değil.

