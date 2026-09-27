# AURA — Geliştirme Yol Haritası

**Son güncelleme:** 2026-09-26 — Öncelik değişikliği: altyapı (auth, cloud GPU, storage, güvenlik) tamamlandı; sıradaki odak **ürünün kendisi** — VTON çıktı kalitesi, arayüz tasarımı, AI stylist deneyimi. Kalan altyapı işleri (plan bazlı kota, maliyet izleme, migration script) bilinçli olarak ertelendi, ürün kalitesi turundan sonra ele alınacak.

---

## ✅ Tamamlanan Altyapı (Faz 0–2)

Bu bölüm sıkıştırılmış bir özet — detaylı denetim geçmişi commit mesajlarında ve geçmiş konuşmalarda duruyor.

### Faz 0 — Vision Pipeline Temeli
- [x] Orientation onay UI + backend koruması (RotNet ensemble, re-normalize, skip_orientation)
- [x] Kategori tespiti (YOLO-boş fallback), boş-sahne yanlış-pozitif düzeltmesi
- [x] Decompression bomb koruması (Java + Vision, header-only inceleme)

### Faz 1a — Production Auth
- [x] BCrypt, register/login/refresh token rotasyonu + replay tespiti (REQUIRES_NEW ile)
- [x] Rate limiting (Bucket4j + Redis, 5/dakika), brute-force kilidi (5 deneme → 15dk)
- [x] JWT secret zorunluluğu, enumeration-önleme

### Faz 1b — Cloud GPU (RunPod Serverless)
- [x] RunPod deploy, gerçek uçtan uca test (register→login→VTON→COMPLETED→DB)
- [x] Timeout/retry/cold-start toleransı, kullanıcı bazlı günlük kota (HTTP 429)
- [x] **5 turluk SSRF denetim zinciri** — Wardrobe SSRF, DNS TOCTOU, TLS host doğrulama, kalıcı DNS ele geçirme, resolveResultBytes — hepsi Java+Python'da kapatıldı
- [x] RunPod worker hata işleme sertleştirmesi (2026-09-27): FAILED ve
      boş-output senaryolarında sahte mock:// URI üretimi kaldırıldı,
      kota iadesi her iki durumda da doğrulandı. Bulgu OpenCode'un
      ücretsiz bir modelle yaptığı bağımsız analizden geldi, Claude Code
      ile denetlendi.

### Faz 2 — Object Storage (Cloudflare R2)
- [x] Base64/DB'den R2'ye taşıma, presigned upload, region=auto (SigV4)
- [x] StorageUrlGuard R2 origin'lerini (endpoint + 3 public host) tanıyor
- [x] Public erişim: bucket başına `.r2.dev` host (custom domain gerektirmeden)
- [x] Gerçek CatVTON modeline geçiş: `torch.xpu` bağımlılık çakışması bulunup düzeltildi (diffusers/transformers/numpy pin'lendi)

**Ertelenen altyapı maddeleri (Faz 3.5'te ele alınacak, aşağıya bakın):** plan bazlı kota, maliyet izleme, eski veri migration script'i, egress proxy.

---

## 🎯 ŞİMDİKİ ODAK — Faz 3: Ürün Kalitesi ve Deneyimi

Buraya kadarki her şey kullanıcının **görmediği** işti. Şimdi kullanıcının **hissedeceği** şeye geçiyoruz. Üç paralel cephe var — sırayla değil, birlikte ilerleyebilir.

### 3a. VTON Çıktı Kalitesi — Post-Processing Katmanı

Şu an CatVTON'un ham çıktısı doğrudan kullanıcıya gidiyor. Hedef: ham model çıktısı ile "mağaza kalitesi görsel" arasındaki farkı kapatan bir işleme katmanı eklemek.

- [ ] **Perspektif düzeltme** — kıyafetin dolaba eklenirken düz, cepheden bakışa normalize edilmesi (homografi/warp)
- [ ] **Crop/kenar temizliği** — arka plan kalıntılarının, kesim hatalarının giderilmesi
- [ ] **Işık/gölge normalizasyonu** — orijinal fotoğraftaki gölge/parlama, VTON çıktısına "yapıştırılmış" gibi durmasın diye düzeltilmesi
- [ ] **"Ütülenmiş" doku temizliği** — kumaş kırışıklıklarının/gürültünün hafif yumuşatılması (aşırıya kaçmadan, gerçekçiliği bozmadan)
- [ ] **Çözünürlük yükseltme** — çıktıya bir upscaling adımı (Real-ESRGAN gibi hafif bir model) eklenmesi
- [ ] Golden-set'i gerçek çeşitlilikte büyütme (farklı kıyafet türü, ten tonu, vücut tipi — şu anki set çoğunlukla tek tip)
- [ ] IDM-VTON ticari lisans durumunun netleştirilmesi (CatVTON'a bağlı kalınacaksa resmi karar olarak kayıt altına alınsın)

**Çıkış kriteri:** VTON çıktısı, "bariz AI üretimi" değil, "gerçek bir ürün fotoğrafı" gibi hissettiriyor.

### 3b. Arayüz Yeniden Tasarımı — Quiet Luxury Pivot (sıcak, açık palet)

**Karar:** Carbon & Champagne'ın koyu kimliğinden, sıcak/açık "quiet luxury"
paletine geçiş (krem zemin, kahve-altın vurgu). Bu bir marka pivotu, küçük
bir ayar değil.

**Onaylanan palet:**
- Zemin: #F2ECDC · Header gradyanı: #C08A4E→#8B5A2B
- Kart: #FFFFFF/#FAF6EC · Ana buton: #3D2B1F
- Metin birincil: #2B2118 · Metin ikincil: #8A7A68 · Nadir vurgu: #D4A548

**Tipografi:** Başlık = Fraunces (serif, karakterli), Gövde/UI = Manrope

- [ ] 3b.0 — Tasarım sistemi temeli (ThemeData, font, ikon paketi, radius/gölge kuralı)
- [ ] 3b.1 — Pilot: Aura AI chat ekranı (en düşük risk, sistemi doğrula)
- [ ] 3b.2 — Dolap ekranı (+ güven skoru gizleme, dil tutarlılığı, duplicate kart kontrolü)
- [ ] 3b.3 — Öneri ekranı (+ özel slider tasarımı)
- [ ] 3b.4 — Arşiv ekranı (+ rozet hiyerarşisi, kırmızı ikon düzeltmesi, boş durum metni)
- [ ] 3b.5 — Raf ekranı IA ayrımı (hesap/çıkış → ayrı Ayarlar ekranı)
- [ ] 3b.6 — Son öz-eleştiri turu (5 ekranın güncel hali, üç ilkeye göre)

**Çıkış kriteri:** Beş ekran de yeni palette/tipografiye geçmiş, ham/teknik
veri kullanıcı arayüzünden temizlenmiş, tek bir tutarlı ikon seti kullanılıyor.

### 3c. AI Stylist Chat Geliştirmesi

Mevcut chat (Ollama tabanlı, llama3.2) — kalitesi/kişiliği hiç ayrıca değerlendirilmedi.

- [ ] Mevcut chat kalitesinin gerçek konuşmalarla test edilmesi — moda tavsiyesi verirken ne kadar isabetli/ilginç?
- [ ] Sistem promptunun, Carbon & Champagne markasına uygun bir "stylist kişiliği" ile zenginleştirilmesi
- [ ] Dolap/hava durumu verisiyle chat'in gerçekten bağlam kullandığının doğrulanması (şu an bağlantı var mı, ne kadar etkin kullanılıyor?)

**Çıkış kriteri:** Kullanıcı stylist'e bir şey sorduğunda, jenerik bir chatbot değil, "kişisel stilistim" hissi alıyor.

---

## 💡 Claude'un Önerileri — Değerlendirmeye Açık Fikirler

Bunlar benim önerim, henüz karara bağlanmadı — hangisi ilginizi çekerse oradan detaylandırırız. Aklıma geldikçe buraya eklemeye ve sohbette de ayrıca belirtmeye devam edeceğim.

- **☆ Tam Kombin VTON** — Şu anki VTON tek parça (`clothType: upper`) gibi görünüyor. Üst+alt+ayakkabıyı **birlikte** render eden bir "tam kombin dene" modu, gerçek kullanım senaryosuna (insanlar tek tişört değil, tüm kombini görmek ister) çok daha yakın olur. Muhtemelen en yüksek kullanıcı-değeri/efor oranına sahip öneri.
- **☆ Kapsül Gardolap Analizi** — Kullanıcının dolabını analiz edip "kışlık dış giyiminiz eksik" gibi somut boşluk tespitleri sunmak. Hem kullanıcı değeri yüksek hem de affiliate gelir modeliyle (Farfetch/SSENSE linkleri) doğrudan örtüşüyor — "eksiğinizi tamamlayın" önerisi doğal bir satın alma tetikleyicisi.
- **☆ Beden/Uyum Tahmini** — Kullanıcının dolabındaki geçmiş verilerden, yeni bir markadaki bedenini tahmin etmek. Affiliate ortaklarının (iade oranını düşürdüğü için) özellikle değer vereceği bir özellik, ticarileşme fazında güçlü bir satış argümanı olur.
- **☆ Ana Ekran Widget'ı** — Zaten var olan Thermodynamic Outfit Engine'i kullanarak, telefonun ana ekranında "bugün hava X, şunu giy" widget'ı. Düşük efor, yüksek görünürlük/günlük kullanım artırıcı.
- **☆ Giysi Kullanım Takibi** — "Bu parçayı 6 aydır giymediniz" gibi nazik hatırlatmalar; sürdürülebilirlik/bilinçli tüketim anlatısına uygun, lüks-bilinçli kullanıcı kitlesiyle örtüşür, ileride bağış/yeniden satış önerisine kapı açar.
- **☆ Çoklu Açı VTON** — Tek kareden değil, ön+yan iki açıdan render, kullanıcının "gerçekten böyle mi duruyor" güvenini artırır (daha uzun vadeli, model karmaşıklığı yüksek).

---

## Faz 3.5 — Ertelenen Altyapı İşleri (Faz 3 sonrası)

- [ ] Plan bazlı kota farklılaştırması (Aura Silver/Black) — Faz 6 ile birlikte, billing entegrasyonuyla
- [ ] Maliyet izleme (RunPod dashboard + günlük harcama alarmı)
- [ ] Eski Base64 kayıtlar için migration script (henüz eski veri yok)
- [ ] Egress proxy / ağ seviyesi SSRF savunması (kapalı beta sonrası değerlendirilecek)

---

## Faz 4 — "3D İncele" Özelliği (Faz 3 ile paralel başlanabilir)

- [ ] Depth-Anything/MiDaS ile dolap fotoğraflarından derinlik haritası
- [ ] Three.js/WebGL tabanlı, Carbon & Champagne temalı "inspect" kartı (parallax + ışık kayması)
- [ ] Flutter entegrasyonu (WebView veya native)
- [ ] Aura Black'e özel konumlandırma

**Çıkış kriteri:** Bir dolap parçasına dokunup döndürüldüğünde inandırıcı bir "3D'ye bakıyorum" hissi var.

---

## Faz 5 — Kapalı Beta

- [ ] 20-50 kişilik kapalı kullanıcı grubu
- [ ] TestFlight (iOS) + Google Play kapalı test kanalı
- [ ] Telemetri: onay UI tetiklenme sıklığı, VTON başarısızlık oranı, kullanıcı takılma noktaları
- [ ] Bu veriyle RotNet/geometri ensemble'ının gerçek veriyle yeniden eğitilmesi

**Çıkış kriteri:** Gerçek kullanıcılar uçtan uca akışı sorunsuz tamamlayabiliyor.

---

## Faz 6 — Ticarileşme Altyapısı (Faz 5 ile paralel başlanabilir)

- [ ] StoreKit (iOS) / Play Billing (Android) — Aura Black abonelik akışı
- [ ] Affiliate/komisyon altyapısı (Farfetch/SSENSE/Beymen)
- [ ] Plan bazlı özellik kısıtlamaları gerçek billing'e bağlanması

**Çıkış kriteri:** Gerçek parayla Aura Black'e geçilebiliyor, kısıtlamalar doğru uygulanıyor.

> 💰 Mağaza komisyonu: Küçük İşletme Programı'na kayıtlıysanız %15, kayıtsız ilk yıl Apple'da %30. Apple Developer $99/yıl, Google Play $25 tek seferlik.

---

## Faz 7 — App Store / Google Play Lansmanı

- [ ] Store listing (Carbon & Champagne diliyle tutarlı)
- [ ] Gizlilik politikası, KVKK/GDPR uyumluluğu (VTON fotoğrafları hassas veri)
- [ ] Mağaza inceleme sürecine hazırlık
- [ ] Soft launch → gözlem → geniş lansman

**Çıkış kriteri:** Uygulama mağazalarda, gerçek kullanıcılar indirebiliyor.

---

## Zaman Çizelgesi Özeti

| Faz | Süre (tahmini) | Durum |
|---|---|---|
| 0-2 — Altyapı | — | ✅ Tamamlandı |
| **3 — Ürün Kalitesi (VTON+UI+Chat)** | **3-4 hafta** | **🎯 Şimdiki odak** |
| 3.5 — Ertelenen altyapı | 1 hafta | Faz 3 sonrası |
| 4 — 3D İncele | 1-2 hafta | Faz 3 ile paralel başlanabilir |
| 5 — Kapalı Beta | 2-3 hafta | — |
| 6 — Ticarileşme | 2 hafta | Faz 5 ile paralel |
| 7 — Lansman | 2-4 hafta | — |

---

## Öncelik Felsefesi — Neden Bu Sıra?

1. **Altyapı olmadan ürün işi anlamsızdı** — güvensiz/çalışmayan bir boru hattı üzerine kalite işi yapıp sonra o boru hattını değiştirmek, yapılan işi çöpe atmak demekti. Bu artık geride kaldı.
2. **Şimdi ürünün kendisi öncelikli** — altyapı görünmez, kalite ve tasarım görünür. Kullanıcı test etmeden önce bu boşluk kapanmalı.
3. **3D İncele, kalite işiyle paralel gidebilir** — farklı bir teknik yüzey (derinlik haritası, WebGL), VTON/UI işini bloklamıyor.
4. **Ertelenen altyapı gerçekten ertelenebilir** — plan bazlı kota ve maliyet izleme, tek kullanıcılı/düşük trafikli bu aşamada acil değil; ticarileşmeden hemen önce yeterli.
5. **Beta, ticarileşmeden önce** — para almadan önce akışın çalıştığını görmek gerekiyor.

---

## Bu Yol Haritasını Nasıl Kullanalım

Her görev için birlikte Cursor promptu hazırlarız (Cursor uygular → Claude Code denetler → siz commit'lersiniz). Görsel/tasarım işleri için Cursor'a farklı türden promptlar (estetik karar odaklı, kod-mantığı değil) gerekecek — bu, üzerinde ayrıca konuşacağımız bir yöntem farkı. Faz tamamlandığında çıkış kriterini gözden geçirip sıradakine geçeriz.