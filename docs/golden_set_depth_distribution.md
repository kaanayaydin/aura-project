# Golden-set depth dağılım sağlık kontrolü

Tarih: 2026-09-19 (Adım 4 güncellemesi). Ölçüm: chroma/alpha hattı
(`studio_rembg_enabled` varsayılan false), polish sonrası
`scores.max_depth_across_edges`.

Geometrik ağırlıklar (0.4 / 0.3 / 0.3) bu turda **değiştirilmedi**.

## Eşik 0.30 — dürüst durum

**0.30 eşiği şu an tek bir negatif örneğe (`askisiz_hafif_saga`, 0.285)
dayanıyor; golden-set büyüdükçe doğrulanacak.**

Önceki metin ("boşluğa dayanır, tek fotoğraf değil") yanıltıcıydı:
pozitif tarafta sentetik/polo çukurları var, negatif tarafta hâlâ
yalnızca hafif_saga. İkinci bağımsız sahte-yaka örneği yok.

## Ölçülen max_depth (mevcut vakalar)

| case | grup | max_depth |
|---|---|---:|
| synthetic_uneck | doğru-yüksek sentetik | 1.000 |
| synthetic_uneck_r180 | doğru-yüksek sentetik | 1.000 |
| synthetic_crewneck | sığ crew FN-risk | 0.431 |
| holdout_polo | hold-out, eğitimde yok | 0.466 |
| askisiz_hafif_saga | **tek negatif** | **0.285** |
| gerçek chroma crew/faz | yaka deliği yok | 0.000–0.013 |

Hold-out polo (0.466) eşiğin üstünde; bu, sığ-ama-gerçek çukurun
sentetik_crewneck ile aynı tarafta kaldığını gösterir. Negatif sınıf
hâlâ n=1.
