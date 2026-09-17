#!/usr/bin/env python3
"""Aura proje gelişim raporunu PDF olarak üretir."""

from __future__ import annotations

import os

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

OUT = os.path.join(os.path.dirname(__file__), "Aura_Proje_Gelisim_Raporu_v0.17.3.pdf")

INK = HexColor("#1A1A1A")
MUTED = HexColor("#4A4A4A")
ACCENT = HexColor("#8B7355")
RULE = HexColor("#D4C4B0")
BG_SOFT = HexColor("#F7F3EE")
HEADER_BG = HexColor("#2C2419")


def resolve_fonts() -> tuple[str, str]:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial Unicode.ttf",
    ]
    bold_path = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
    for path in candidates:
        if not os.path.exists(path):
            continue
        try:
            pdfmetrics.registerFont(TTFont("AuraSans", path))
            if os.path.exists(bold_path):
                pdfmetrics.registerFont(TTFont("AuraSans-Bold", bold_path))
                return "AuraSans", "AuraSans-Bold"
            return "AuraSans", "AuraSans"
        except Exception:
            continue
    return "Helvetica", "Helvetica-Bold"


def build() -> str:
    font_name, bold_name = resolve_fonts()
    styles = getSampleStyleSheet()

    custom = {
        "CoverTitle": ParagraphStyle(
            "CoverTitle",
            fontName=bold_name,
            fontSize=26,
            leading=32,
            textColor=HEADER_BG,
            alignment=TA_CENTER,
            spaceAfter=8,
        ),
        "CoverSub": ParagraphStyle(
            "CoverSub",
            fontName=font_name,
            fontSize=12,
            leading=16,
            textColor=MUTED,
            alignment=TA_CENTER,
            spaceAfter=6,
        ),
        "H1Custom": ParagraphStyle(
            "H1Custom",
            fontName=bold_name,
            fontSize=16,
            leading=22,
            textColor=HEADER_BG,
            spaceBefore=18,
            spaceAfter=8,
        ),
        "H2Custom": ParagraphStyle(
            "H2Custom",
            fontName=bold_name,
            fontSize=12.5,
            leading=17,
            textColor=ACCENT,
            spaceBefore=12,
            spaceAfter=6,
        ),
        "BodyCustom": ParagraphStyle(
            "BodyCustom",
            fontName=font_name,
            fontSize=9.5,
            leading=13.5,
            textColor=INK,
            alignment=TA_JUSTIFY,
            spaceAfter=6,
        ),
        "BulletCustom": ParagraphStyle(
            "BulletCustom",
            fontName=font_name,
            fontSize=9.5,
            leading=13,
            textColor=INK,
            leftIndent=12,
            spaceAfter=3,
        ),
        "MetaCustom": ParagraphStyle(
            "MetaCustom",
            fontName=font_name,
            fontSize=8.5,
            leading=11,
            textColor=MUTED,
            spaceAfter=4,
        ),
        "CaptionCustom": ParagraphStyle(
            "CaptionCustom",
            fontName=font_name,
            fontSize=8,
            leading=10,
            textColor=MUTED,
            alignment=TA_CENTER,
            spaceBefore=4,
            spaceAfter=10,
        ),
        "TOCCustom": ParagraphStyle(
            "TOCCustom",
            fontName=font_name,
            fontSize=10,
            leading=15,
            textColor=INK,
            leftIndent=6,
            spaceAfter=3,
        ),
        "TableCell": ParagraphStyle(
            "TableCell",
            fontName=font_name,
            fontSize=8.5,
            leading=11,
            textColor=INK,
        ),
        "TableHead": ParagraphStyle(
            "TableHead",
            fontName=bold_name,
            fontSize=8.5,
            leading=11,
            textColor=HexColor("#FFFFFF"),
        ),
    }
    for style in custom.values():
        styles.add(style)

    def p(text: str, style: str = "BodyCustom") -> Paragraph:
        return Paragraph(text.replace("\n", "<br/>"), styles[style])

    def bullet(text: str) -> Paragraph:
        return Paragraph(f"• {text}", styles["BulletCustom"])

    def hr() -> HRFlowable:
        return HRFlowable(width="100%", thickness=0.6, color=RULE, spaceBefore=4, spaceAfter=8)

    def table(rows: list, widths: list) -> Table:
        t = Table(rows, colWidths=widths)
        t.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
                    ("BACKGROUND", (0, 1), (-1, -1), BG_SOFT),
                    ("GRID", (0, 0), (-1, -1), 0.4, RULE),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 6),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                    ("TOPPADDING", (0, 0), (-1, -1), 4),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                ]
            )
        )
        return t

    story: list = []

    # Cover
    story.append(Spacer(1, 3.0 * cm))
    story.append(p("AURA", "CoverTitle"))
    story.append(p("Kişisel İmaj Orkestratörü", "CoverSub"))
    story.append(Spacer(1, 0.35 * cm))
    story.append(hr())
    story.append(p("<b>Proje Gelişim ve Durum Raporu</b>", "CoverSub"))
    story.append(p("Sürüm kapsamı: v0.1.0 → v0.17.3", "CoverSub"))
    story.append(p("Rapor tarihi: 13 Eylül 2026", "CoverSub"))
    story.append(Spacer(1, 1.0 * cm))
    story.append(
        p(
            "Bu belge, Aura projesinin iskelet kurulumundan güncel güvenlik mimarisi kapanışına "
            "kadar tamamlanan tüm kritik aşamaları, teknik kararları, entegrasyonları ve mevcut "
            "durumu sistematik biçimde özetler. Kaynak izlenebilirlik için kök dizindeki "
            "<b>DEVLOG.md</b> ile birlikte okunabilir."
        )
    )
    story.append(Spacer(1, 0.6 * cm))
    story.append(
        table(
            [
                [p("<b>Alan</b>", "TableHead"), p("<b>Değer</b>", "TableHead")],
                [p("Ürün", "TableCell"), p("Aura — AI kişisel imaj orkestratörü", "TableCell")],
                [p("Güncel sürüm", "TableCell"), p("v0.17.3", "TableCell")],
                [
                    p("Backend", "TableCell"),
                    p("Java 21 · Spring Boot 3 · JPA · JWT", "TableCell"),
                ],
                [p("Mobil", "TableCell"), p("Flutter · Dart · Riverpod", "TableCell")],
                [
                    p("Vizyon", "TableCell"),
                    p("Python · FastAPI · YOLO · SAM · CLIP", "TableCell"),
                ],
                [
                    p("VTON", "TableCell"),
                    p("Python · FastAPI · Celery · CatVTON · SCHP", "TableCell"),
                ],
                [
                    p("Güvenlik durumu", "TableCell"),
                    p("Kullanıcı domain API’leri JWT ile kilitli", "TableCell"),
                ],
            ],
            [4.2 * cm, 11.5 * cm],
        )
    )
    story.append(PageBreak())

    # TOC
    story.append(p("1. İçindekiler", "H1Custom"))
    story.append(hr())
    for item in [
        "2. Yönetici Özeti",
        "3. Proje Vizyonu ve Mimari Genel Bakış",
        "4. Teknoloji Yığını",
        "5. Gelişim Yolculuğu (Kronolojik Aşamalar)",
        "6. Sanal Try-On (VTON) Hatı — Detay",
        "7. Güvenlik Mimarisi Kapanışı (v0.17.1–v0.17.3)",
        "8. Flutter İstemci Durumu",
        "9. API Yüzeyi ve Kimlik Matrisi",
        "10. Test ve Doğrulama Durumu",
        "11. Şu An Neredeyiz?",
        "12. Bilinen Sınırlar ve Bilinçli Borçlar",
        "13. Önerilen Sonraki Adımlar",
        "14. Sonuç",
    ]:
        story.append(p(item, "TOCCustom"))
    story.append(PageBreak())

    # 2
    story.append(p("2. Yönetici Özeti", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Aura, kullanıcının dolabındaki gerçek giysilerden yola çıkarak hava durumu, etkinlik "
            "bağlamı, renk uyumu ve parfüm eşleştirmesiyle kombin öneren; Vision ile fotoğraftan "
            "parça kesip etiketleyen; Virtual Try-On ile seçilen parçayı kişinin üzerinde "
            "görselleştiren; Aura AI sohbeti ile stil danışmanlığı sunan uçtan uca bir MVP ürünüdür."
        )
    )
    story.append(
        p(
            "11–13 Eylül 2026 aralığında proje, boş bir monorepodan çalışır prototipe dönüşmüştür. "
            "Vizyon boru hattı (YOLO → SAM → CLIP), Spring Boot dolap/öneri/parfüm/chat/VTON API’leri, "
            "Flutter mobil kabuk, CatVTON tabanlı try-on worker ve son olarak Spring Security JWT ile "
            "kullanıcıya özel tüm domainlerin kimlik kilidi tamamlanmıştır."
        )
    )
    story.append(
        p(
            "<b>Bugünkü kritik sonuç:</b> Kullanıcı verisine dokunan API yüzeyi (dolap, favoriler, "
            "VTON, öneri, sohbet, parfüm rafı) tek ve tutarlı bir JWT kimlik modeline bağlıdır. "
            "Token’sız istekler <b>401</b>, sahiplik ihlalleri <b>403</b> döner. Hava durumu ve demo "
            "token endpoint’i bilinçli olarak açık bırakılmıştır."
        )
    )

    # 3
    story.append(p("3. Proje Vizyonu ve Mimari Genel Bakış", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Aura monorepo dört ana servisten oluşur. İstemci Flutter üzerinden Java backend’e "
            "konuşur; Vision ve VTON Python servisleri backend veya mobil tarafından orkestre edilir."
        )
    )
    story.append(
        table(
            [
                [
                    p("<b>Servis</b>", "TableHead"),
                    p("<b>Rol</b>", "TableHead"),
                    p("<b>Port / Not</b>", "TableHead"),
                ],
                [
                    p("aura-backend", "TableCell"),
                    p(
                        "Kimlik, dolap, öneri, chat, parfüm, favoriler, VTON orkestrasyonu",
                        "TableCell",
                    ),
                    p("8080 · Spring Boot", "TableCell"),
                ],
                [
                    p("aura-mobile", "TableCell"),
                    p("macOS/iOS/Android istemci; Riverpod state", "TableCell"),
                    p("Flutter", "TableCell"),
                ],
                [
                    p("aura-vision", "TableCell"),
                    p("YOLO tespit, SAM kesim, CLIP etiket; opsiyonel dolap sync", "TableCell"),
                    p("FastAPI", "TableCell"),
                ],
                [
                    p("aura-vton", "TableCell"),
                    p(
                        "CatVTON try-on worker; SCHP mask; Celery/Redis; serverless hazırlık",
                        "TableCell",
                    ),
                    p("8001 · GPU/CPU", "TableCell"),
                ],
            ],
            [3.2 * cm, 8.5 * cm, 4.0 * cm],
        )
    )
    story.append(Spacer(1, 0.3 * cm))
    story.append(
        p(
            "Veri akışının özü: Kullanıcı fotoğraf yükler → Vision analiz eder → (opsiyonel) JWT ile "
            "Java dolaba yazar → Öneri motoru dolaptan kombin üretir → Favorilere kaydedilebilir → "
            "VTON ile kişi fotoğrafı + garment birleşir → Lookbook’a alınabilir. Sohbet katmanı "
            "dolap/raf/hava bağlamını Ollama’ya (veya fallback’e) enjekte eder; WardrobeGuardrail "
            "uydurma ürün önermeyi metin düzeyinde kısıtlar."
        )
    )

    # 4
    story.append(p("4. Teknoloji Yığını", "H1Custom"))
    story.append(hr())
    for line in [
        "<b>Backend:</b> Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security (stateless JWT HS256), H2 (test) / PostgreSQL hedefi, RFC 7807 ProblemDetail hata modeli.",
        "<b>Mobil:</b> Flutter, Dart 3.11+, Riverpod Notifier/AsyncNotifier, http paketi, image_picker, markdown sohbet baloncukları.",
        "<b>Vision:</b> Python 3.10+, FastAPI, Ultralytics YOLOv8, Segment Anything (SAM), OpenCLIP/Transformers CLIP, httpx ile backend sync.",
        "<b>VTON:</b> FastAPI internal API, Celery + Redis kuyruk, SCHP ONNX INT8 AutoMasker, CatVTON diffüzyon, pose guiding (pseudo-keypoints), Docker/serverless handler.",
        "<b>AI Chat:</b> Lokal Ollama entegrasyonu + lüks tonlu deterministic fallback; anti-halüsinasyon prompt + post-filter guardrail.",
    ]:
        story.append(bullet(line))

    story.append(PageBreak())

    # 5 Timeline
    story.append(p("5. Gelişim Yolculuğu (Kronolojik Aşamalar)", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Aşağıdaki bölümler DEVLOG kayıtlarına ve tamamlanan MVP dilimlerine dayanır. "
            "Her aşama ‘ne yaptık / neden önemli’ çerçevesinde anlatılır."
        )
    )

    story.append(p("5.1 Temel Vizyon İskeleti — v0.1.0 → v0.4.0 (11.09.2026)", "H2Custom"))
    for b in [
        "<b>v0.1.0:</b> aura-vision FastAPI iskeleti; metadata analizi ve /health. Amaç: Vision servisinin bağımsız koşabileceğini doğrulamak; AI modelleri bilinçli olarak sonraya bırakıldı.",
        "<b>v0.2.0:</b> YOLOv8 Nano nesne tespiti. Bounding box’lar üretildi; ağırlık otomatik indirme; ModelUnavailableError → HTTP 503. COCO sınıf setinin moda odaklı olmadığı teknik borç olarak not edildi.",
        "<b>v0.3.0:</b> SAM ile piksel kesimi. Box-prompt maske; RGBA cutout; hata durumunda bbox_crop yedeği. Kesimler sonraki CLIP aşamasına beslendi.",
        "<b>v0.4.0:</b> CLIP anlamsal etiketleme. category + categoryConfidence; RGBA→RGB zemin düzleştirme; batch sınıflandırma. YOLO→SAM→CLIP boru hattı tamamlandı.",
    ]:
        story.append(bullet(b))
    story.append(
        p(
            "<i>Bu dilim, dolaba yazılacak verinin kaynağını oluşturdu. Sonraki tüm öneri ve VTON akışları bu etiketlere dayanır.</i>",
            "MetaCustom",
        )
    )

    story.append(p("5.2 Backend Doğumu ve Dolap Döngüsü — v0.5.0 → v0.7.0", "H2Custom"))
    for b in [
        "<b>v0.5.0:</b> Vision’da MPS/CUDA/CPU cihaz çözümleyici; Spring Boot 3.5 + Java 21 iskeleti; User / WardrobeItem / Perfume JPA ilişkileri. MPS float64 SAM hatası kök neden olarak çözüldü (~1.6x hızlanma).",
        "<b>v0.6.0:</b> PostgreSQL hedefi + dolap API + Python↔Java entegrasyonu. Vision çıktısının backend’e yazılabilir hale gelmesi.",
        "<b>v0.7.0:</b> Asenkron yazma + dolap okuma (liste/görsel/tekil). ‘Fotoğraf → analiz → dolap → okuma’ döngüsü kapandı.",
    ]:
        story.append(bullet(b))
    story.append(
        p(
            "<i>Aura yalnızca Vision demosu olmaktan çıkıp kalıcı kullanıcı dolabına sahip bir backend ürününe dönüştü.</i>",
            "MetaCustom",
        )
    )

    story.append(p("5.3 Stil Motoru, Mobil ve Parfüm — v0.8.0 → v0.11.0", "H2Custom"))
    for b in [
        "<b>v0.8.0:</b> Bağlam ve Termodinamik Motoru (kural tabanlı). Occasion + sıcaklık/nem → sezon bandı → outfit planı.",
        "<b>v0.9.0:</b> Flutter mobil istemci (dolap + öneri). Riverpod + ApiService.",
        "<b>v0.10.0:</b> Parfüm & koku motoru; öneri cevabına perfumeRecommendation.",
        "<b>v0.11.0:</b> Kullanıcı parfüm rafı + foto→Vision→DB döngüsünün mobilde pekiştirilmesi.",
    ]:
        story.append(bullet(b))

    story.append(p("5.4 Akıllı Filtreleme, Hava ve Favoriler — v0.12.0 → v0.13.1", "H2Custom"))
    for b in [
        "<b>v0.12.0:</b> Gelişmiş kombin & renk uyumu (ColorHarmony), skorlar, akıllı filtreleme.",
        "<b>v0.13.0:</b> Akıllı hava durumu (WeatherService, useAutoWeather).",
        "<b>v0.13.1:</b> Favoriler sekmesi UX — kaydet / listele / sil.",
    ]:
        story.append(bullet(b))

    story.append(p("5.5 Aura AI Sohbet ve Guardrail — v0.14.0 → v0.14.3", "H2Custom"))
    for b in [
        "<b>v0.14.0:</b> Lokal LLM (Ollama) stilist sohbeti; dolap + raf + hava system prompt’a enjekte.",
        "<b>v0.14.1:</b> Lüks moda editörü tonu + markdown sohbet baloncukları.",
        "<b>v0.14.2:</b> Anti-halüsinasyon prompt sıkılaştırması.",
        "<b>v0.14.3:</b> Strict WardrobeGuardrail — model cevabı metin düzeyinde filtrelenir.",
    ]:
        story.append(bullet(b))

    story.append(PageBreak())

    story.append(p("5.6 Virtual Try-On Doğuşu — v0.15.0 → v0.15.3", "H2Custom"))
    for b in [
        "<b>v0.15.0:</b> Java VTON iskeleti — VtonJob, status makinesi, controller/service.",
        "<b>v0.15.1:</b> Python VTON worker (FastAPI + Celery + Redis), mock model yolu.",
        "<b>v0.15.2:</b> Java garment iletimi + Flutter VTON UI; constructor hotfix.",
        "<b>v0.15.3:</b> Gerçek kişi fotoğrafı (kamera/galeri) + önizleme.",
    ]:
        story.append(bullet(b))

    story.append(p("5.7 Gerçek CatVTON, Proxy, Maske, Lookbook, Pose — v0.16.0 → v0.16.5", "H2Custom"))
    for b in [
        "<b>v0.16.0:</b> Gerçek CatVTON + kalıcı çıktı.",
        "<b>v0.16.1:</b> Java görsel proxy + Flutter Before/After split view.",
        "<b>v0.16.2:</b> SCHP AutoMasker (ONNX INT8) agnostic mask.",
        "<b>v0.16.3:</b> ClothTypeMapper — kategori → upper/lower/overall; doğru bölge maskesi.",
        "<b>v0.16.4:</b> Lookbook arşivi + güvenli görsel erişimi (sonradan JWT’ye taşındı).",
        "<b>v0.16.5:</b> Pose guiding & drapaj: SCHP pseudo-keypoints, soft-structure residual, steps=40, guidance=2.0.",
    ]:
        story.append(bullet(b))

    story.append(p("5.8 Bulut GPU Hazırlığı — v0.17.0", "H2Custom"))
    story.append(
        p(
            "aura-vton RunPod/Modal uyumlu container, entrypoint, serverless_handler, prewarm script "
            "ve Java timeout ayarlarıyla cloud GPU’ya taşınmaya hazırlandı. Yerelde mock model ile "
            "maliyet sıfır tutulabilir. EXECUTION_MODE=api|celery|serverless|prewarm. Gerçek RunPod "
            "endpoint bağlama hâlâ sıradaki adımdır."
        )
    )

    # 6 VTON
    story.append(p("6. Sanal Try-On (VTON) Hatı — Detay", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Orkestrasyon: Flutter JWT alır → Java authenticated userId ile job oluşturur → dolaptan "
            "garment + kişi görseli worker’a enqueue → SCHP mask (+ pose guide) → CatVTON → Java "
            "status/proxy → isteğe bağlı Lookbook."
        )
    )
    for b in [
        "<b>ClothTypeMapper:</b> Yanlış bölge maskeleme riskini azaltır.",
        "<b>Ownership:</b> Job ve sonuç görselleri yalnızca token sahibine (403).",
        "<b>Lookbook:</b> COMPLETED işler arşivlenebilir.",
        "<b>Serverless:</b> Dockerfile + handler ile scale-to-zero hedefi hazır.",
    ]:
        story.append(bullet(b))

    story.append(PageBreak())

    # 7 Security
    story.append(p("7. Güvenlik Mimarisi Kapanışı (v0.17.1 – v0.17.3)", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Erken MVP’de kimlik çoğunlukla query/body <b>userId</b> veya <b>X-Aura-User-Id</b> ile "
            "taşınıyordu. Bu model istemci tarafından taklit edilebilir. v0.17.x diliminde güvenilir "
            "kimlik kaynağı JWT Principal’a çekildi."
        )
    )

    story.append(p("7.1 v0.17.1 — JWT temeli + VTON kilidi", "H2Custom"))
    for b in [
        "Spring Security stateless: JwtService (HS256), JwtAuthenticationFilter, AuraPrincipal, SecurityUtils.requireUserId().",
        "Demo login: POST /api/v1/aura/auth/token → accessToken + userId.",
        "VTON path’leri authenticated(); kimlik Principal’dan.",
        "Flutter authSessionProvider.ensure(); VTON/Lookbook/Before-After Bearer.",
        "Testler: token’sız 401, yabancı job 403.",
    ]:
        story.append(bullet(b))

    story.append(p("7.2 v0.17.2 — Gardırop & Favoriler", "H2Custom"))
    for b in [
        "/api/v1/wardrobe/** ve /api/v1/aura/favorites/** authenticated().",
        "Controller’larda elle userId kaldırıldı; sahiplik 403 / yok kayıt 404.",
        "Flutter dolap/favori Bearer; WardrobeNotifier & FavoritesNotifier ensure().",
        "Vision wardrobe_sync: önce /auth/token, sonra Bearer ile POST /wardrobe/items.",
    ]:
        story.append(bullet(b))

    story.append(p("7.3 v0.17.3 — Suggest, Chat & Perfume", "H2Custom"))
    for b in [
        "POST /aura/suggest, POST /aura/chat, /api/v1/user/perfumes/** authenticated().",
        "AuraService yalnızca JWT kullanıcısının dolabından öneri üretir.",
        "Chat guardrail yalnızca authenticated kullanıcının dolap + rafını görür.",
        "Yabancı parfüm silme → PerfumeOwnershipException → 403.",
        "Flutter suggest/chat/perfume Bearer + ensure().",
        "Bilinçli açık: /api/v1/aura/auth/** ve /api/v1/weather/**.",
    ]:
        story.append(bullet(b))

    story.append(p("7.4 Kimlik modeli özeti", "H2Custom"))
    story.append(
        p(
            "Tüm korunan domainlerde tek kural: <b>Authorization: Bearer &lt;jwt&gt;</b>. "
            "DTO’lardaki opsiyonel userId alanları geriye uyumluluk için kalabilir ancak sunucu "
            "tarafında yok sayılır. Böylece ‘impostor body userId’ saldırı yüzeyi kapanır."
        )
    )

    # 8 Flutter
    story.append(p("8. Flutter İstemci Durumu", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "HomeShell sekmeleri: Dolap, Öneri, Aura AI, Arşiv (Favoriler | Lookbook), Parfüm Rafı. "
            "ApiService, accessTokenProvider üzerinden oturumdaki JWT’yi okur."
        )
    )
    for b in [
        "<b>authSessionProvider:</b> Demo token bellek içi; ensure() ile lazy login.",
        "<b>Wardrobe / Favorites / Lookbook / Perfume:</b> build/refresh sırasında ensure().",
        "<b>Suggestion & Chat:</b> istek öncesi ensure + Bearer.",
        "<b>VTON UI:</b> kişi fotoğrafı, onay, durum, Before/After, Lookbook’a ekle.",
        "<b>Sürüm:</b> pubspec 0.17.3+1; flutter test 14/14.",
    ]:
        story.append(bullet(b))

    story.append(PageBreak())

    # 9 API
    story.append(p("9. API Yüzeyi ve Kimlik Matrisi", "H1Custom"))
    story.append(hr())
    story.append(
        table(
            [
                [
                    p("<b>Alan</b>", "TableHead"),
                    p("<b>Örnek path</b>", "TableHead"),
                    p("<b>Auth</b>", "TableHead"),
                ],
                [p("Auth", "TableCell"), p("POST /api/v1/aura/auth/token", "TableCell"), p("Public", "TableCell")],
                [p("Weather", "TableCell"), p("GET /api/v1/weather/current", "TableCell"), p("Public", "TableCell")],
                [p("Wardrobe", "TableCell"), p("/api/v1/wardrobe/**", "TableCell"), p("JWT", "TableCell")],
                [p("Suggest", "TableCell"), p("POST /api/v1/aura/suggest", "TableCell"), p("JWT", "TableCell")],
                [p("Chat", "TableCell"), p("POST /api/v1/aura/chat", "TableCell"), p("JWT", "TableCell")],
                [p("Favorites", "TableCell"), p("/api/v1/aura/favorites/**", "TableCell"), p("JWT", "TableCell")],
                [p("Perfume", "TableCell"), p("/api/v1/user/perfumes/**", "TableCell"), p("JWT", "TableCell")],
                [p("VTON", "TableCell"), p("/api/v1/aura/vton/**", "TableCell"), p("JWT", "TableCell")],
            ],
            [3.2 * cm, 9.0 * cm, 3.5 * cm],
        )
    )
    story.append(Spacer(1, 0.25 * cm))
    story.append(
        p(
            "HTTP anlamları: <b>401</b> kimlik yok/geçersiz; <b>403</b> kimlik var ama kaynak başkasına "
            "ait; <b>404</b> kaynak yok; <b>422</b> dolap yetersiz (öneri). Hatalar ProblemDetail "
            "(RFC 7807) ile döner."
        )
    )

    # 10 Tests
    story.append(p("10. Test ve Doğrulama Durumu", "H1Custom"))
    story.append(hr())
    for b in [
        "<b>aura-backend:</b> Maven tam suite yeşil (Wardrobe, Favorites, VTON, Suggest, Chat, Perfume, Auth entegrasyon testleri).",
        "<b>aura-mobile:</b> flutter test 14/14.",
        "<b>aura-vton:</b> pytest — v0.17.0 kaydında 29/29 (pose/mask/schemas/serverless).",
        "Kritik güvenlik senaryoları kodda assert edilir: unauthenticated→401, foreign ownership→403.",
    ]:
        story.append(bullet(b))

    # 11 Where
    story.append(p("11. Şu An Neredeyiz?", "H1Custom"))
    story.append(hr())
    story.append(p("Aura, <b>çalışır uçtan uca MVP</b> aşamasındadır. Ürün demosu şu hikâyeyi taşıyabilir:"))
    for b in [
        "Fotoğraftan kıyafet kes/etiketle ve dolaba kaydet (Vision + JWT sync veya Flutter fallback).",
        "Hava + occasion ile dolaptan kombin + parfüm öner.",
        "Öneriyi favorilere kaydet; Aura AI ile dolap bağlamında sohbet et.",
        "Kişi fotoğrafı ile VTON çalıştır, Before/After izle, Lookbook’a ekle.",
        "Tüm kullanıcı verisi yolları Bearer JWT ile korunur.",
    ]:
        story.append(bullet(b))
    story.append(
        p(
            "Olgunluk: işlevsel derinlik yüksek; üretim sertleştirmesi (gerçek auth, bulut GPU bağlama, "
            "CDN, gizlilik) sıradaki iş paketidir. Sürüm etiketi <b>0.17.3</b> — güvenlik mimarisinin "
            "domain kapanışını işaretler."
        )
    )
    story.append(Spacer(1, 0.2 * cm))
    story.append(
        table(
            [
                [
                    p("<b>Katman</b>", "TableHead"),
                    p("<b>Durum</b>", "TableHead"),
                    p("<b>Not</b>", "TableHead"),
                ],
                [
                    p("Vision boru hattı", "TableCell"),
                    p("Olgun MVP", "TableCell"),
                    p("YOLO+SAM+CLIP çalışır", "TableCell"),
                ],
                [
                    p("Öneri motoru", "TableCell"),
                    p("Olgun MVP", "TableCell"),
                    p("Kural tabanlı; LLM değil", "TableCell"),
                ],
                [
                    p("Chat", "TableCell"),
                    p("MVP + guardrail", "TableCell"),
                    p("Ollama veya fallback", "TableCell"),
                ],
                [
                    p("VTON kalitesi", "TableCell"),
                    p("İyileştirilmiş prototip", "TableCell"),
                    p("SCHP+pose+CatVTON", "TableCell"),
                ],
                [
                    p("Güvenlik (API)", "TableCell"),
                    p("Domain kilidi tamam", "TableCell"),
                    p("Demo JWT; şifresiz login", "TableCell"),
                ],
                [
                    p("Cloud GPU", "TableCell"),
                    p("Hazırlık tamam", "TableCell"),
                    p("Endpoint bağlama bekleniyor", "TableCell"),
                ],
                [
                    p("Üretim auth", "TableCell"),
                    p("Eksik", "TableCell"),
                    p("Şifre/OAuth/refresh yok", "TableCell"),
                ],
            ],
            [4.0 * cm, 4.2 * cm, 7.5 * cm],
        )
    )

    story.append(PageBreak())

    # 12 Debt
    story.append(p("12. Bilinen Sınırlar ve Bilinçli Borçlar", "H1Custom"))
    story.append(hr())
    for b in [
        "<b>Demo authentication:</b> /auth/token username/userId ile token üretir; parola, refresh, revoke, rate-limit yok.",
        "<b>Weather public:</b> Kimlik gerektirmez (bilinçli). İstenirse JWT altına alınabilir.",
        "<b>Öneri motoru:</b> Hâlâ kural tabanlı; LLM ile yeniden sıralama yok.",
        "<b>VTON:</b> Gerçek RunPod/Modal endpoint ve production CDN henüz bağlanmamış olabilir; DWPose ONNX parse ileri iş.",
        "<b>Veri saklama:</b> Görseller base64/TEXT alanlarında; üretimde object storage tercih edilmeli.",
        "<b>Vision sınır:</b> Genel YOLO person tespiti kombin fotoğrafında parça ayrıştırmayı zorlaştırabilir; moda odaklı detektör ileride gerekebilir.",
        "<b>Çok kullanıcılı sertleştirme:</b> Hesap yaşam döngüsü, e-posta doğrulama, cihaz bağlama yok.",
    ]:
        story.append(bullet(b))

    # 13 Next
    story.append(p("13. Önerilen Sonraki Adımlar", "H1Custom"))
    story.append(hr())
    story.append(p("Öncelik sırasıyla önerilen yol haritası:"))
    for b in [
        "<b>P0 — Gerçek login:</b> Şifre hash (bcrypt/argon2) veya OAuth; refresh token; mobil güvenli saklama.",
        "<b>P0 — Cloud VTON endpoint:</b> AURA_VTON_WORKER_URL’ü RunPod HTTPS’e bağla; cold-start SLA ölç.",
        "<b>P1 — Object storage + CDN:</b> VTON/dolap görsellerini DB dışına taşı.",
        "<b>P1 — Observability:</b> Structured logging, request-id, VTON latency / 401 oranı metrikleri.",
        "<b>P2 — Weather kilidi / API gateway:</b> İsteğe bağlı authenticated weather; global rate limit.",
        "<b>P2 — VTON kalite:</b> DWPose ONNX; A/B guidance/steps; insan değerlendirme seti.",
    ]:
        story.append(bullet(b))

    # 14 Conclusion
    story.append(p("14. Sonuç", "H1Custom"))
    story.append(hr())
    story.append(
        p(
            "Aura projesi kısa bir zaman diliminde vizyon, stil motoru, mobil deneyim, sanal deneme "
            "ve güvenlik katmanlarını bir araya getiren tutarlı bir MVP’ye ulaşmıştır. v0.17.3 "
            "itibarıyla teknik borçun en kritik parçası olan ‘sahte kimlik ile başkasının dolabına "
            "erişim’ yüzeyi kapatılmıştır. Bundan sonraki değer üretimi, gerçek kullanıcı hesapları "
            "ve bulut GPU üzerinde ölçeklenebilir try-on ile ürünleştirme tarafındadır."
        )
    )
    story.append(Spacer(1, 0.5 * cm))
    story.append(
        p(
            "Kaynak izlenebilirlik: kök dizindeki <b>DEVLOG.md</b> her kritik dilimin tarih, dosya, "
            "entegrasyon ve test komutlarını tutar. Bu rapor, DEVLOG’un yönetici/ürün okuması için "
            "derlenmiş anlatısal karşılığıdır.",
            "MetaCustom",
        )
    )
    story.append(Spacer(1, 1.0 * cm))
    story.append(hr())
    story.append(p("— Aura Project · Gelişim Raporu · v0.17.3 · 13.09.2026 —", "CaptionCustom"))

    def add_page_number(canvas, doc) -> None:
        canvas.saveState()
        page = canvas.getPageNumber()
        if page > 1:
            canvas.setFont(font_name, 8)
            canvas.setFillColor(MUTED)
            canvas.drawString(1.8 * cm, 1.2 * cm, "Aura · Gelişim Raporu")
            canvas.drawRightString(A4[0] - 1.8 * cm, 1.2 * cm, f"Sayfa {page}")
            canvas.setStrokeColor(RULE)
            canvas.line(1.8 * cm, 1.45 * cm, A4[0] - 1.8 * cm, 1.45 * cm)
        canvas.restoreState()

    doc = SimpleDocTemplate(
        OUT,
        pagesize=A4,
        leftMargin=1.8 * cm,
        rightMargin=1.8 * cm,
        topMargin=1.8 * cm,
        bottomMargin=2.0 * cm,
        title="Aura Proje Gelişim Raporu v0.17.3",
        author="Aura Project",
    )
    doc.build(story, onFirstPage=add_page_number, onLaterPages=add_page_number)
    return OUT


if __name__ == "__main__":
    path = build()
    print(path)
    print(f"size={os.path.getsize(path)} bytes")
