# RotNet ensemble — golden-set doğrulama (v0.21.0)

Val set (sentetik self-supervised, 88 örnek): acc=1.00, 90↔270 karışıklığı=0.
Bu, eğitim dağılımının kendi içinde ayrıldığını gösterir; asıl ölçüt golden-set.

## Confusion matrix (val)

Satır=gerçek, sütun=tahmin; etiketler 0 / 90 / 180 / 270.

```
22  0  0  0
 0 22  0  0
 0  0 22  0
 0  0  0 22
```

## Golden-set ensemble (13 vaka)

| case | önce (geometri) | RotNet | ensemble | deg | yaka doğru mü? |
|---|---|---|---|---:|---|
| synthetic_uneck | high top | top 0.99 | high top | 0 | evet |
| synthetic_crewneck | high top | top 1.00 | high top | 0 | evet |
| synthetic_uneck_r90 | **low bottom deg=0** | left 0.95 | **medium left** | **270** | evet (yaka üste) |
| synthetic_uneck_r180 | high bottom | bottom 0.99 | high bottom | 180 | evet |
| synthetic_uneck_r270 | **low bottom deg=0** | right 0.97 | **medium right** | **90** | evet (yaka üste) |
| askisiz_duz_aci | low | top 0.95 | medium top | 0 | evet (zaten dik) |
| askisiz_perspektif_golge | low | top 0.98 | medium top | 0 | evet |
| askili_yaka_yukarida_duz | low | top 0.68 | low left | 0 | rotasyon yok (RotNet <0.8) |
| askisiz_arka_yuz | low | top 0.99 | medium top | 0 | evet |
| askisiz_hafif_saga | low left (depth 0.285) | top 0.99 | medium top | 0 | evet; **270 yok** |
| real_duz_r90 | **low deg=0** | left 0.99 | **high left** | **270** | evet |
| real_duz_r180 | **low deg=0** | bottom 0.97 | **medium bottom** | **180** | evet |
| real_duz_r270 | **low deg=0** | right 0.99 | **medium right** | **90** | evet |

## 90° / 270° — önce / sonra

Üç turdur `low + deg=0` dönen asıl hedef:

- **synthetic_uneck_r90:** önce low/deg=0 (geometri bottom). Sonra RotNet `left` 0.95 → medium + 270. Çıktıda yaka üstte. (Askı adımı yatay tişörtte bir kolu kesebiliyor — yön değil, ayrı borç.)
- **synthetic_uneck_r270:** önce low/deg=0. Sonra RotNet `right` 0.97 → medium + 90. Yaka üstte.
- **real_duz_r90 / r270:** önce low/deg=0. Sonra doğru kenar, görsel olarak dik tişört.

Dosya adı `r90` CCW üretimdir: yaka **solda** (eski `ground_truth_neck=right` etiket hatasıydı; piksele göre düzeltildi).

## hafif_saga

- Geometri: `no_edge_shows_real_notch_depth`, left, 0.285 < 0.30. **Kapı duruyor.**
- RotNet: `top` 0.995 (chroma blob üzerinde aşırı özgüven — izlenmeli).
- Ensemble: medium + top + **deg=0**. Eski false-high+270 **uygulanmıyor**.
- "Low olarak reddedildi" değil; "yanlış 270 uygulanmadı, yaka zaten üstte".

## Oranlar (13 golden vaka)

- `low_confidence_rate` = 1/13 = **0.077** (yalnızca askılı)
- `medium_confidence_rate` = 8/13 = **0.615**
- `high_confidence_rate` = 4/13 = **0.308**

Flutter onay UI henüz yok. Medium oranı, onayın sık tetikleneceğinin ön göstergesi.

## Bilinçli sınırlar

- Val 100% ≠ sorun kapandı; ölçüt golden 90/270 çıktısının yakayı üste getirmesi.
- Geometrik ağırlıklar değişmedi.
- Yatay flip yok.
- Eğitim yalnızca upright / expected-high mask + sentetik silüet; low-confidence etiket yok.
