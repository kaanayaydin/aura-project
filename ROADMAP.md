# AURA — MVP'den Lansmana Detaylı Yol Haritası

**Baz alınan durum:** v0.21.x civarı — Vision pipeline (YOLO+SAM+CLIP), orientation/RotNet ensemble, VTON (CatVTON+SCHP), JWT auth, Flutter onay UI zinciri tamamlanmak üzere.
**Yaklaşım:** Her faz bir önceki fazın üzerine kurulur; hiçbir faz "bitmeden" bir sonrakine geçilmez ama fazlar arasında paralel çalışılabilecek işler ayrıca işaretli.

---

## Faz 0 — Şu An Bitirilmekte Olan (1-2 hafta)

Bu, üzerinde çalıştığımız zincir — bitmeden hiçbir şeye başlamayın, çünkü VTON'un ve dolabın temel doğruluğu buna bağlı.

- [x] Orientation onay UI + backend koruması (re-normalize, skip_orientation, rembg tutarlılığı) — **neredeyse tamam**
- [x] Kalan küçük temizlikler (test izolasyonu, docstring düzeltmesi)
- [ ] Kategori tespiti (YOLO-boş fallback) golden-set regresyonu genişletme

**Çıkış kriteri:** Dolaba yüklenen her fotoğraf (düz, eğik, perspektifli) tutarlı, doğru yönlü, 3:4 formatında saklanıyor; kullanıcı kararı hiçbir koşulda sessizce ezilmiyor.

---

## Faz 1 — Gerçek Auth + Cloud GPU Bağlama (2-3 hafta)

Bunu daha önce konuşmuştuk: cloud GPU'yu şifresiz/rate-limitsiz bir auth ile açmak maliyet riski. Sıra önemli.

### 1a. Production-grade Auth (~1 hafta) ✅ TAMAMLANDI (2026-09-24)
- [x] BCrypt parola, register/login/refresh token rotasyonu
      (refresh rotasyonu + replay tespiti gerçek Postgres/HTTP ile
      doğrulandı, REQUIRES_NEW ile transaction sınırı düzeltildi)
- [x] Rate limiting (Bucket4j + Redis, IP bazlı, 5/dakika üretim ayarı)
- [x] Brute-force koruması, hesap kilitleme (5 deneme → 15dk LOCKED,
      otomatik açılma doğrulandı)
- [x] JWT secret zorunluluğu (sessiz zayıf varsayılan kaldırıldı,
      env set edilmezse uygulama başlamıyor)
- [x] Enumeration-önleme (bilinmeyen email/yanlış şifre aynı mesaj)

### 1b. Cloud GPU Endpoint (~1 hafta)
- [ ] RunPod Serverless veya Modal'a VTON worker deploy
- [ ] Java tarafında timeout + retry + cold-start toleransı
- [x] Kullanıcı bazlı VTON kota sayacı (günlük sabit limit, AURA_VTON_DAILY_LIMIT
      varsayılan 5; HTTP 429, gerçek testlerle doğrulandı — VtonQuotaServiceTest,
      VtonQuotaControllerTest, mutasyon testiyle kilit teyit edildi)
- [ ] **Plan bazlı** kota farklılaştırması (Aura Silver/Black ayrımı) — henüz
      yok, User modelinde plan/tier alanı hiç tanımlı değil. Bu, Faz 6
      (Ticarileşme) ile birlikte, billing entegrasyonuyla yapılacak.
- [ ] Maliyet izleme (RunPod dashboard + basit bir günlük harcama alarmı)

**Çıkış kriteri:** Gerçek bir kullanıcı hesabıyla giriş yapıp, gerçek bulut GPU'da bir VTON isteği çalıştırabiliyorsunuz; kötü niyetli/sınırsız istek maliyeti şişiremiyor.

> 💰 **Maliyet notu (RunPod Serverless, Ağustos 2026 fiyatlarıyla):** Bir A5000 (24GB) worker saniyede ~$0.00026 (Flex) — bir VTON isteği ~15-30 sn sürerse **istek başına ~$0.004-0.008**. 1.000 ücretsiz-tier VTON denemesi/ay ≈ **$4-8**. Cold-start (worker uykudayken ilk istek) 5-15 sn ek gecikme getirir ama fazladan ücret değildir; sürekli "warm" tutmak isterseniz (Active worker, %40 indirimli) günde birkaç saat açık tutmak bile aylık $15-40 arası ek maliyet demektir — beta aşamasında buna gerek yok, scale-to-zero'da kalın.

---

## Faz 2 — Object Storage + Veri Sağlamlaştırma (1-2 hafta, Faz 1 ile kısmen paralel)

- [ ] Görselleri (dolap, VTON sonuçları) Base64/DB'den S3/R2'ye taşı
- [ ] Presigned URL akışı (zaten VTON mimarisinde tasarlanmıştı, şimdi gerçek storage'a bağlayın)
- [ ] Eski Base64 kayıtlar için arka planda migration script'i
- [ ] CDN (CloudFront/Cloudflare) — özellikle VTON sonuçları için

**Çıkış kriteri:** Veritabanınız artık dev binary blob'larla şişmiyor, görseller hızlı ve ölçeklenebilir servis ediliyor.

> 💰 **Maliyet notu (Cloudflare R2, 2026 fiyatlarıyla):** Depolama $0.015/GB-ay, **egress (dışa veri çıkışı) ücretsiz** (S3'ün aksine — bu, sık görüntülenen VTON/dolap fotoğrafları için önemli bir fark, aylık faturanızı öngörülebilir tutar). 1.000 kullanıcı × ortalama 30 dolap fotoğrafı + birkaç VTON sonucu (~500KB/görsel) ≈ 20-30GB → **aylık ~$0.30-0.50 depolama, $0 egress**. CDN (Cloudflare, R2 ile aynı ekosistem) ek maliyetsiz eklenebilir. Bu faz, beklenenin aksine bütçenin en ucuz kalemlerinden biri.

---

## Faz 3 — VTON Kalite ve Ölçek Sertleştirmesi (2-3 hafta)

- [ ] Gerçek kullanıcı trafiğiyle (beta test grubu) VTON kalitesini gözlemleyin — kumaş drapajı, ışık uyumu
- [ ] Golden-set'i gerçek çeşitlilikte büyütün (farklı kıyafet türleri, farklı ten tonları, farklı vücut tipleri) — şu ana kadarki set çoğunlukla beyaz tişört, bu VTON'un genel güvenilirliğini test etmiyor
- [ ] IDM-VTON'un ticari lisans durumunu netleştirin (hukuki, daha önce konuşmuştuk) — CatVTON'a bağlı kalacaksanız bunu resmi bir karar olarak kayıt altına alın
- [ ] Prewarm/scale-to-zero maliyet dengesini gerçek trafik verisiyle ayarlayın

**Çıkış kriteri:** VTON, dar bir demo setinin ötesinde, çeşitli gerçek kullanıcı fotoğraflarında güvenilir sonuç veriyor; maliyetler öngörülebilir.

---

## Faz 4 — "3D İncele" Özelliği (Hızlı Kazanım, Faz 3 ile paralel başlanabilir, 1-2 hafta)

Az önce konuştuğumuz özellik — MVP'nin **farklılaştırıcı** parçası, yatırımcı/kullanıcı gösterimi için değerli.

- [ ] Depth-Anything (veya MiDaS) ile dolap fotoğraflarından derinlik haritası üretimi (Vision servisine yeni bir adım)
- [ ] Three.js/WebGL tabanlı, Carbon & Champagne temalı "inspect" kartı — parallax + ışık kayması efekti
- [ ] Flutter'da bu kartı gösterecek bir WebView veya native entegrasyon
- [ ] Sadece "Aura Black" kullanıcılarına özel bir özellik olarak konumlandırma (premium'u haklı çıkaran somut bir fark)

**Çıkış kriteri:** Bir dolap parçasına dokunup parmakla/fareyle hafifçe döndürüldüğünde inandırıcı bir "3D'ye bakıyorum" hissi var.

**Not:** Bu fazı isterseniz Faz 1-3 ile paralel, ayrı bir "araştırma dalı" olarak da ilerletebilirsiniz — VTON/backend sertleştirmesini bloklamaz.

---

## Faz 5 — Kapalı Beta (2-3 hafta)

- [ ] 20-50 kişilik kapalı kullanıcı grubu (arkadaş çevresi, moda ilgili topluluklar)
- [ ] TestFlight (iOS) + Google Play kapalı test kanalı
- [ ] Telemetri: onay UI ne sıklıkla tetikleniyor, hangi adımlarda kullanıcı takılıyor, VTON başarısızlık oranı
- [ ] Bu veriyle: RotNet/geometri ensemble'ını gerçek kullanıcı verisiyle yeniden eğitme fırsatı (golden-set büyür)

**Çıkış kriteri:** Gerçek kullanıcılar uçtan uca (fotoğraf çek → dolaba ekle → öneri al → VTON dene → beğen) akışı sorunsuz tamamlayabiliyor; kritik bug'lar temizlenmiş.

---

## Faz 6 — Ticarileşme Altyapısı (2 hafta, Faz 5 ile paralel başlanabilir)

- [ ] Stripe (iOS için StoreKit, Android için Play Billing) entegrasyonu — "Aura Black" abonelik akışı
- [ ] Affiliate/komisyon altyapısı (Farfetch/SSENSE/Beymen API'leri veya ortaklık linkleri) — bu bir sonraki faza da ertelenebilir, MVP'de olmak zorunda değil
- [ ] Plan bazlı özellik kısıtlamaları (25 parça limiti, günlük öneri sayısı, VTON kotası) — kod tarafında zaten bazı yerlerde referans var, şimdi gerçek billing'e bağlayın

**Çıkış kriteri:** Bir kullanıcı gerçek parayla "Aura Black"e geçebiliyor, kısıtlamalar doğru uygulanıyor.

> 💰 **Maliyet notu (mağaza komisyonları, 2026 fiyatlarıyla):** $14.99/ay'lık abonelikte Apple/Google'ın Küçük İşletme Programı'na (yıllık $1M altı gelir) kayıtlıysanız komisyon **%15** — yani her abonelikten **~$2.25 mağazaya gider**, size ~$12.74 kalır (Apple'da 2. yıldan itibaren zaten %15'e düşer, Google'da baştan %15). Program'a kayıt olmayı unutmayın, aksi halde ilk yıl Apple'da %30 kesinti (~$4.50/ay) uygulanır. Bunun dışında: Apple Developer Program **$99/yıl** (zorunlu), Google Play **$25 tek seferlik**. Yani lansmana kadar toplam mağaza maliyeti ~$124, sonrasında sadece komisyon oranı işler.

---

## Faz 7 — App Store / Google Play Lansmanı (2-4 hafta)

- [ ] Store listing (ekran görüntüleri, açıklama, Carbon & Champagne marka diliyle tutarlı)
- [ ] Gizlilik politikası, KVKK/GDPR uyumluluğu (kullanıcı fotoğrafları hassas veri sayılabilir, özellikle VTON için — bunu hukuki olarak netleştirin)
- [ ] App Store / Play Store inceleme sürecine hazırlık (VTON gibi "kullanıcı fotoğrafı işleyen" özellikler bazen ek inceleme gerektirir)
- [ ] Soft launch (tek bir pazar/ülke) → gözlem → geniş lansman

**Çıkış kriteri:** Uygulama mağazalarda, gerçek kullanıcılar indirebiliyor.

---

## Zaman Çizelgesi Özeti

| Faz | Süre (tahmini) | Paralel çalışılabilir mi |
|---|---|---|
| 0 — Orientation zincirini bitir | 1-2 hafta | — |
| 1 — Auth + Cloud GPU | 2-3 hafta | Kısmen (1a/1b ayrı kişi/zaman) |
| 2 — Object Storage | 1-2 hafta | Faz 1 ile paralel |
| 3 — VTON Sertleştirme | 2-3 hafta | — |
| 4 — 3D İncele | 1-2 hafta | Faz 1-3 ile paralel başlanabilir |
| 5 — Kapalı Beta | 2-3 hafta | — |
| 6 — Ticarileşme | 2 hafta | Faz 5 ile paralel |
| 7 — Lansman | 2-4 hafta | — |

**Toplam, tek geliştirici olarak, gerçekçi tempo ile: yaklaşık 3.5-4.5 ay** (roadmap'inizdeki 2027 hedefinden çok daha erken — haklısınız, mevcut ilerleme hızınız ve disiplininiz göz önüne alındığında bu, 2026 sonu/2027 başı gibi bir pencereye sığar).

---

## Maliyet Özeti (aylık, tahmini, 2026 fiyatlarıyla)

Bu rakamlar **kaba tahminler** — gerçek trafiğiniz oluştuğunda değişecektir, ama bütçe planlaması için bir başlangıç noktası:

| Kalem | Ne zaman başlar | Aylık tahmini (küçük ölçek: ~500-1.000 aktif kullanıcı) |
|---|---|---|
| Cloud GPU (VTON, RunPod Serverless) | Faz 1b | $10-40 (scale-to-zero, kullanım bazlı) |
| Object Storage (Cloudflare R2) | Faz 2 | $1-5 (egress ücretsiz olduğu için düşük kalır) |
| CDN | Faz 2 | Genelde R2 ile birlikte $0'a yakın (Cloudflare ekosistemi) |
| Apple Developer Program | Faz 7 öncesi | $99/**yıl** (aylığa böldüğünüzde ~$8.25) |
| Google Play Developer | Faz 7 öncesi | $25 **tek seferlik** |
| Mağaza komisyonu | Gelir oluştukça | Gelirin %15'i (Küçük İşletme Programı'na kayıtlıysanız) |
| Redis (rate-limit + kuyruk) | Faz 1a | $0-10 (küçük ölçekte ücretsiz tier'lar genelde yeterli — Upstash, Redis Cloud) |
| PostgreSQL (managed) | Faz 1a | $0-15 (Supabase/Neon gibi sağlayıcıların ücretsiz tier'ı küçük ölçekte yeterli olabilir) |

**Kabaca toplam, ilk aylarda (düşük trafik):** GPU maliyeti hariç ayda **$15-50** bandında, sonra kullanıcı/VTON hacmi arttıkça esas olarak **GPU maliyeti** ölçekle birlikte büyüyecek kalem olacak — bu yüzden Faz 1b'deki kota sayacı gerçekten kritik, orası kontrolsüz büyüyebilecek tek kalem.

**Not:** Bu tahminler yaklaşık; her fazın başında Cursor/Claude Code turlarında gerçek kullanım verisiyle (RunPod dashboard, R2 kullanım raporu) bu rakamları güncelleyip daha kesin bütçe çıkarabiliriz.

---

## Öncelik Felsefesi — Neden Bu Sıra?

1. **Faz 0 bitmeden hiçbir şeye başlamayın** — temel doğruluk (orientation/format) olmadan üstüne inşa edeceğiniz her şey (VTON kalitesi, beta geri bildirimi) kirli veri üzerine kurulur.
2. **Auth, cloud GPU'dan önce** — daha önce netleştirdiğimiz maliyet-risk sıralaması hâlâ geçerli.
3. **3D İncele özelliğini erken, paralel bir dal olarak düşünün** — hem düşük riskli (mevcut pipeline'ı bozmuyor) hem de yatırımcı/kullanıcı gösterimi için yüksek etkili, bu yüzden "sona bırakılacak bir güzellik" değil, erken bir farklılaştırıcı olarak konumlandırdım.
4. **Beta, ticarileşmeden önce** — para almadan önce gerçek kullanıcıların akışı tamamlayabildiğini görmek istiyorsunuz.
5. **Lansman en sona** — mağaza inceleme süreçleri öngörülemez, en son ve en esnek zaman dilimini buna ayırın.

---

## Bu Yol Haritasını Nasıl Kullanalım

Her faz başladığında, o fazın ilk büyük görevi için birlikte Cursor promptu hazırlarız (tıpkı Faz 0'da yaptığımız gibi: Cursor uygular → Claude Code denetler → siz commit'lersiniz). Faz tamamlandığında, çıkış kriterini birlikte gözden geçirip bir sonraki faza geçeriz. İsterseniz bu dosyayı projenizin kök dizinine (`ROADMAP.md` gibi) koyup zaman içinde işaretleyerek (checkbox) ilerlememizi takip edebiliriz.
