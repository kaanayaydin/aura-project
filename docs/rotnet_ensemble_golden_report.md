# RotNet ensemble — golden-set (HEAD)

**Son güncelleme:** 2026-09-19 (UTC+3).  
**Commit:** `a95b737` (`git rev-parse --short HEAD` bu koşuda).  
**Nasıl üretildi:** `aura-vision/scripts/dump_golden_orientation_report.py` — her vaka
`garment_normalizer.normalize(..., force_rembg=False, debug=True)`. Sayılar bu
dosyadan veya eski rapordan kopyalanmadı. Ham `decision.json`:
`aura-vision/debug_output/_golden_report_head/<id>/decision.json` (gitignore).

Önceki sürüm (v0.21.0 tablosu: `real_duz_r90` high/270°, `real_duz_r270`
medium/90°) **sızıntılı / golden-eğitimli model** dönemine aitti; HEAD ile
karıştırılmamalı.

Yalnızca `final_confidence==high` otomatik kardinal rotasyon uygular.
`medium` → `skipped_pending_confirmation`, `low` → `skipped_low_confidence`.
Flutter onay UI yok.

---

## Val (eğitim split’i) — `rotnet_v1.metrics.json`

Kaynak-split sonrası: `train_n=400`, `val_n=80`, `val_acc=1.0`,
`confusion_90_vs_270_count=0`. Satır=gerçek, sütun=tahmin; etiketler 0/90/180/270.

```
20  0  0  0
 0 20  0  0
 0  0 20  0
 0  0  0 20
```

Bu matris **sentetik T ailesi** val’dir (kaynaklar `syn_u_c`, `syn_u_d`).
Golden-set değildir. Val %100 ≠ yön sorunu kapandı.

1-NN piksel sızıntısı (`rotnet_v1.leak.json` after): `exact_match_rate=0.0`,
`knn1_same_source_rate=0.0`, `knn1_classification_accuracy=1.0` (80/80).
Sınıf doğruluğunun düşmemesi, “model golden’ı ezberledi” kanıtı **değildir**
(aşağıda hipotez).

---

## Golden-set ensemble (18 vaka, HEAD canlı)

`geo` = geometrik `chosen_edge` + confidence. `RotNet` = `predicted_edge` +
softmax conf. `ens` = `ensemble.final_confidence` / `final_edge` / `reason`.
`deg` = `rotation_deg_applied`. GT = `ground_truth_neck` (piksel yaka).

| case | GT | geo | RotNet | ens | deg | method |
|---|---|---|---|---|---:|---|
| synthetic_uneck | top | high top d=1.0 | top 0.9611 | high top agreement | 0 | none |
| synthetic_crewneck | top | high top d=0.431 | top 0.9772 | high top agreement | 0 | none |
| synthetic_uneck_r90 | left | low bottom d=1.0 | left 0.7962 | **low** bottom disagreement | 0 | skipped_low_confidence |
| synthetic_uneck_r180 | bottom | high bottom d=1.0 | bottom 0.9272 | high bottom agreement | **180** | cv2.ROTATE_180 |
| synthetic_uneck_r270 | right | low bottom d=1.0 | right 0.9148 | medium right rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| askisiz_duz_aci | top | low bottom d=0.008 | top 0.8840 | medium top rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| askisiz_perspektif_golge | top | low left d=0.0 | top 1.0000 | medium top rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| askili_yaka_yukarida_duz | top | low left d=0.0 | bottom 0.6885 | **low** left disagreement | 0 | skipped_low_confidence |
| askisiz_arka_yuz | top | low bottom d=0.013 | top 0.9496 | medium top rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| askisiz_hafif_saga | top | low left d=0.285 | top 0.9215 | medium top rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| real_duz_r90 | left | low left d=0.004 | left 0.8734 | **medium** left agreement_geometry_low | **0** | skipped_pending_confirmation |
| real_duz_r180 | bottom | low top d=0.005 | bottom 0.9924 | medium bottom rotnet_override_low_geometry | 0 | skipped_pending_confirmation |
| real_duz_r270 | right | low top d=0.012 | **bottom 0.6598** | **low** top disagreement | **0** | skipped_low_confidence |
| holdout_hoodie | top | low top d=0.0 | top 0.7557 | medium top agreement_geometry_low | 0 | skipped_pending_confirmation |
| holdout_longsleeve | top | low top d=0.0 | right 0.3805 | **low** top disagreement | 0 | skipped_low_confidence |
| holdout_polo | top | high top d=0.466 | top 0.9636 | high top agreement | 0 | none |
| holdout_aline_dress | top | low top d=0.0 | left 0.9095 | **low** top rotnet_override_blocked_no_notch | 0 | skipped_low_confidence |
| holdout_real_perspective | top | low left d=0.0 | top 1.0000 | medium top rotnet_override_low_geometry | 0 | skipped_pending_confirmation |

`d=` geometrik `max_depth_across_edges`. `orient_min_absolute_depth=0.30`
hâlâ tek negatif `askisiz_hafif_saga` (0.285) boşluğundan; dağılım
doğrulanmadı (`docs/golden_set_depth_distribution.md`).

### Ensemble oranları (18 vaka)

- high: 4/18 = **0.222** (`synthetic_uneck`, `synthetic_crewneck`, `synthetic_uneck_r180`, `holdout_polo`)
- medium: 9/18 = **0.500**
- low: 5/18 = **0.278** (`synthetic_uneck_r90`, `askili_yaka_yukarida_duz`, `real_duz_r270`, `holdout_longsleeve`, `holdout_aline_dress`)

Otomatik uygulanan tek kardinal: `synthetic_uneck_r180` (high, 180°).

---

## 90° / 270° — HEAD gerçeği (kök açık)

Dosya adı `r90` CCW üretim: yaka **solda**; `r270` → yaka **sağda**.

| case | suggested doğru mu? | uygulanır mı? | durum |
|---|---|---|---|
| synthetic_uneck_r90 | hayır (bottom; GT left). RotNet left 0.7962 &lt; 0.8 | hayır, low | faz hatası **açık** |
| synthetic_uneck_r270 | evet (right). RotNet 0.9148 | hayır, medium köprü | doğru kenar önerilir, **görüntü dönmez** |
| real_duz_r90 | evet (left). RotNet 0.8734 | hayır, medium köprü | eski rapordaki high/270 **yok** |
| real_duz_r270 | hayır (top; GT right). RotNet bottom 0.6598 | hayır, low | eski rapordaki medium/90 **yok** |

Regresyon durdu (kanıtsız high+270 ve sessiz medium uygulama yok). Kök
(geometri 90/270 fazı, gerçek fotoğrafta RotNet) çözülmedi.

---

## `holdout_aline_dress` (known failure)

RotNet left/270 conf=0.9095, `max_depth=0.0`. Ensemble
`rotnet_override_blocked_no_notch` → low, deg=0. RotNet hâlâ yanlış sınıf.

---

## `askisiz_hafif_saga`

Geometri `no_edge_shows_real_notch_depth`, left, 0.285 &lt; 0.30. RotNet top
0.9215. Ensemble medium top, deg=0 (`skipped_pending_confirmation`). Eski
false-high+270 uygulanmıyor.

---

## Doğrulanmamış hipotez: “ezberleme”

**Kanıtlanmadı.** Kaynak-split sonrası golden eğitimde yok
(`golden_set_in_training: false`); 1-NN exact-match 0. Eski golden’da 90/270’in
daha iyi görünmesi “model o görüntüleri ezberledi” diye **kanıtlanmaz**:
(1) o dönemdeki ONNX artık yok, A/B yok; (2) val hâlâ aynı sentetik T ailesi,
1-NN sınıf doğruluğu 1.0 — siluetten sınıf okumak ayrı şey; (3) `real_duz_r270`
düşüşü (`right~0.99` → `bottom 0.6598`) yeniden eğitimin dağılım değişikliği
olabilir. Hipotezi olgu gibi yazma.

---

## Bilinçli sınırlar

- `medium` otomatik dönmez (`467d00e` köprü, `TODO(flutter-confirm)`).
- `rotnet_override_blocked_no_notch`: geo low + RotNet left/right + depth &lt; 0.30 → low.
- Geometrik ağırlıklar 0.4/0.3/0.3 bu raporda değişmedi.
- Yatay flip yok.
- Perspektif kapsam dışı (`holdout_real_perspective`).

Yeniden üret: `cd aura-vision && PYTHONPATH=. python scripts/dump_golden_orientation_report.py`

---

## Ek: ham `decision.json` (ilk / sorunlu / known-failure)

Kopya değil; 2026-09-19T13:03Z, commit `a95b737`.

### `real_duz_r90` (eski tabloda high/270 idi)

```json
{
  "job_id": "real_duz_r90",
  "rotation_deg_applied": 0,
  "rotation_method": "skipped_pending_confirmation",
  "rotation_suggested": "left",
  "requires_confirmation": true,
  "low_confidence": false,
  "pipeline_git_commit": "a95b737",
  "timestamp": "2026-09-19T13:03:20.713092+00:00",
  "cutout_source": "chroma",
  "ensemble": {
    "final_confidence": "medium",
    "final_edge": "left",
    "reason": "agreement_geometry_low",
    "rotnet_confidence": 0.8734,
    "geometric_confidence": "low"
  },
  "rotnet": {
    "predicted_class": 270,
    "predicted_edge": "left",
    "confidence": 0.8734,
    "probs": {"0": 0.0568, "90": 0.0036, "180": 0.0662, "270": 0.8734},
    "available": true
  },
  "geometric": {
    "chosen_edge": "left",
    "confidence": "low",
    "low_confidence": true,
    "score_gap": 0.112,
    "max_depth_across_edges": 0.004,
    "decision_note": "no_edge_shows_real_notch_depth"
  },
  "ensemble_confidence": "medium"
}
```

### `real_duz_r270` (eski tabloda medium/90 idi)

```json
{
  "job_id": "real_duz_r270",
  "rotation_deg_applied": 0,
  "rotation_method": "skipped_low_confidence",
  "rotation_suggested": "top",
  "requires_confirmation": true,
  "low_confidence": true,
  "pipeline_git_commit": "a95b737",
  "timestamp": "2026-09-19T13:03:21.589444+00:00",
  "cutout_source": "chroma",
  "ensemble": {
    "final_confidence": "low",
    "final_edge": "top",
    "reason": "disagreement",
    "rotnet_confidence": 0.6598,
    "geometric_confidence": "low"
  },
  "rotnet": {
    "predicted_class": 180,
    "predicted_edge": "bottom",
    "confidence": 0.6598,
    "probs": {"0": 0.0466, "90": 0.2916, "180": 0.6598, "270": 0.0019},
    "available": true
  },
  "geometric": {
    "chosen_edge": "top",
    "confidence": "low",
    "low_confidence": true,
    "score_gap": 0.062,
    "max_depth_across_edges": 0.012,
    "decision_note": "no_edge_shows_real_notch_depth"
  },
  "ensemble_confidence": "low"
}
```

### `holdout_aline_dress`

```json
{
  "job_id": "holdout_aline_dress",
  "rotation_deg_applied": 0,
  "rotation_method": "skipped_low_confidence",
  "rotation_suggested": "top",
  "requires_confirmation": true,
  "low_confidence": true,
  "pipeline_git_commit": "a95b737",
  "timestamp": "2026-09-19T13:03:21.753850+00:00",
  "cutout_source": "alpha",
  "ensemble": {
    "final_confidence": "low",
    "final_edge": "top",
    "reason": "rotnet_override_blocked_no_notch",
    "rotnet_confidence": 0.9095,
    "geometric_confidence": "low"
  },
  "rotnet": {
    "predicted_class": 270,
    "predicted_edge": "left",
    "confidence": 0.9095,
    "probs": {"0": 0.0259, "90": 0.0547, "180": 0.0099, "270": 0.9095},
    "available": true
  },
  "geometric": {
    "chosen_edge": "top",
    "confidence": "low",
    "low_confidence": true,
    "score_gap": 0.0,
    "max_depth_across_edges": 0.0,
    "decision_note": "no_edge_shows_real_notch_depth"
  },
  "ensemble_confidence": "low"
}
```
