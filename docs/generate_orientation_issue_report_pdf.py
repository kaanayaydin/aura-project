#!/usr/bin/env python3
"""Garment Orientation sorun analizi PDF raporu üretir."""

from __future__ import annotations

import os

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

OUT = os.path.join(
    os.path.dirname(__file__),
    "Aura_Garment_Orientation_Sorun_Analizi_Raporu.pdf",
)

INK = HexColor("#1A1A1A")
MUTED = HexColor("#4A4A4A")
ACCENT = HexColor("#8B7355")
RULE = HexColor("#D4C4B0")
BG_SOFT = HexColor("#F7F3EE")
HEADER_BG = HexColor("#2C2419")
WARN = HexColor("#8B3A2A")


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


def P(text: str, style: ParagraphStyle) -> Paragraph:
    return Paragraph(text.replace("\n", "<br/>"), style)


def build() -> str:
    font_name, bold_name = resolve_fonts()
    styles = getSampleStyleSheet()

    custom = {
        "CoverTitle": ParagraphStyle(
            "CoverTitle",
            fontName=bold_name,
            fontSize=22,
            leading=28,
            textColor=HEADER_BG,
            alignment=TA_CENTER,
            spaceAfter=10,
        ),
        "CoverSub": ParagraphStyle(
            "CoverSub",
            fontName=font_name,
            fontSize=11,
            leading=15,
            textColor=MUTED,
            alignment=TA_CENTER,
            spaceAfter=4,
        ),
        "H1": ParagraphStyle(
            "H1R",
            fontName=bold_name,
            fontSize=14,
            leading=18,
            textColor=HEADER_BG,
            spaceBefore=14,
            spaceAfter=8,
        ),
        "H2": ParagraphStyle(
            "H2R",
            fontName=bold_name,
            fontSize=12,
            leading=15,
            textColor=ACCENT,
            spaceBefore=10,
            spaceAfter=5,
        ),
        "Body": ParagraphStyle(
            "BodyR",
            fontName=font_name,
            fontSize=9.5,
            leading=13.5,
            textColor=INK,
            alignment=TA_JUSTIFY,
            spaceAfter=6,
        ),
        "Bullet": ParagraphStyle(
            "BulletR",
            fontName=font_name,
            fontSize=9.5,
            leading=13,
            textColor=INK,
            leftIndent=8,
            spaceAfter=3,
        ),
        "Callout": ParagraphStyle(
            "CalloutR",
            fontName=font_name,
            fontSize=9.5,
            leading=13,
            textColor=WARN,
            backColor=BG_SOFT,
            borderPadding=6,
            spaceBefore=6,
            spaceAfter=8,
        ),
        "Mono": ParagraphStyle(
            "MonoR",
            fontName=font_name,
            fontSize=8.5,
            leading=11.5,
            textColor=MUTED,
            leftIndent=4,
            spaceAfter=4,
        ),
        "Footer": ParagraphStyle(
            "FooterR",
            fontName=font_name,
            fontSize=8,
            textColor=MUTED,
            alignment=TA_CENTER,
        ),
        "TableCell": ParagraphStyle(
            "TableCellR",
            fontName=font_name,
            fontSize=8,
            leading=10.5,
            textColor=INK,
        ),
        "TableHead": ParagraphStyle(
            "TableHeadR",
            fontName=bold_name,
            fontSize=8,
            leading=10.5,
            textColor=HexColor("#FFFFFF"),
        ),
    }

    doc = SimpleDocTemplate(
        OUT,
        pagesize=A4,
        leftMargin=1.8 * cm,
        rightMargin=1.8 * cm,
        topMargin=1.6 * cm,
        bottomMargin=1.6 * cm,
        title="Aura Garment Orientation — Sorun Analizi Raporu",
        author="Aura Engineering",
    )

    story: list = []

    # --- Kapak ---
    story.append(Spacer(1, 2.2 * cm))
    story.append(P("AURA PROJECT", custom["CoverSub"]))
    story.append(P("Garment Studio Orientation", custom["CoverTitle"]))
    story.append(P("Sorun Analizi ve Deneyim Raporu", custom["CoverTitle"]))
    story.append(Spacer(1, 0.4 * cm))
    story.append(HRFlowable(width="100%", thickness=1, color=RULE, spaceAfter=12))
    story.append(
        P(
            "Kapsam: v0.20.2 Garment Polish — Deskew, Yaka Yönelimi, 90° Faz Hatası<br/>"
            "Modül: aura-vision / app/services/garment_polish.py<br/>"
            "Tarih: 18–19 Eylül 2026<br/>"
            "Durum: Yönelim hâlâ üretimde kırılgan; kök neden geometrik belirsizlik",
            custom["CoverSub"],
        )
    )
    story.append(Spacer(1, 1.2 * cm))
    story.append(
        P(
            "<b>Özet cümle:</b> Askı silme ve sürekli açı (deskew) büyük ölçüde çözüldü; "
            "asıl sıkıştığımız nokta, tişörtün <b>hangi kenarının “yaka”</b> olduğuna "
            "güvenilir karar verip bunu <b>fiilen uygulanan</b> 90°/180° dönüşe bağlamak.",
            custom["Body"],
        )
    )
    story.append(PageBreak())

    # --- 1. Yönetici özeti ---
    story.append(P("1. Yönetici Özeti", custom["H1"]))
    story.append(
        P(
            "Son birkaç iterasyonda kullanıcıya görünen semptom neredeyse hep aynıydı: "
            "stüdyo arka planı ve cutout başarılı; tişört “düz” duruyor ama <b>doğru yönde değil</b>. "
            "Bazen 45° çapraz, bazen tam yatay (yaka sağda / etek solda), bazen ters (yaka altta). "
            "Her turda bir katmanı düzelttik; bir sonraki turda bir üst katman açığa çıktı.",
            custom["Body"],
        )
    )
    story.append(
        P(
            "Bu, klasik bir <b>onion-peeling</b> (soğan kabuğu) hata örüntüsüdür: her düzeltme "
            "bir önceki hatayı maskeleyen bir sonraki hatayı görünür kılar. Alttaki gerçek zorluk "
            "ise tek bir formül değil; <b>silüet geometrisinin çok anlamlı (ambiguous)</b> olmasıdır.",
            custom["Body"],
        )
    )

    story.append(P("Kısa kronoloji", custom["H2"]))
    rows = [
        [
            P("Aşama", custom["TableHead"]),
            P("Görünen semptom", custom["TableHead"]),
            P("O sırada ne sandık / ne yaptık", custom["TableHead"]),
        ],
        [
            P("Askı + polish", custom["TableCell"]),
            P("Askı maskede; kırışıklık var", custom["TableCell"]),
            P("Hanger morph + bilateral press eklendi — bu katman büyük ölçüde OK", custom["TableCell"]),
        ],
        [
            P("PCA ±35°", custom["TableCell"]),
            P("Yaka sağa/yana bakıyor", custom["TableCell"]),
            P("Küçük açı düzeltmesi yetmedi; 90° fazı PCA ile çözülemez", custom["TableCell"]),
        ],
        [
            P("4×90° kardinal arama", custom["TableCell"]),
            P("Bazen etek yukarı (180° ters)", custom["TableCell"]),
            P("Çukur skoru etek/koltuk altını yaka sandı", custom["TableCell"]),
        ],
        [
            P("Continuous deskew", custom["TableCell"]),
            P("Eksenler dümdüz ama tişört yatay", custom["TableCell"]),
            P("minAreaRect uzun kenarı (kol–kol) dikeye çekti; gövde yatay kaldı", custom["TableCell"]),
        ],
        [
            P("Yaka→Kuzey + cv2.rotate", custom["TableCell"]),
            P("Hâlâ yaka Doğu’da kalabiliyor", custom["TableCell"]),
            P("Skor Doğu’yu seçmiyor veya dönüş pipeline’da fiilen uygulanmıyor/geri alınıyor hissi", custom["TableCell"]),
        ],
    ]
    t = Table(rows, colWidths=[3.2 * cm, 5.2 * cm, 7.8 * cm])
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
                ("BACKGROUND", (0, 1), (-1, -1), BG_SOFT),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("GRID", (0, 0), (-1, -1), 0.4, RULE),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    story.append(t)
    story.append(Spacer(1, 0.3 * cm))
    story.append(
        P(
            "<b>Asıl sorun (tek cümle):</b> Cutout sonrası silüetten “bu kenar yakadır” bilgisini "
            "güvenilir çıkarmak ve o kararı <b>değişmez bir rotasyon komutuna</b> bağlamak. "
            "Uzun/kısa kenar, omuz genişliği ve yaka çukuru birbirini taklit edebiliyor.",
            custom["Callout"],
        )
    )

    # --- 2. Ne istiyoruz ---
    story.append(P("2. Hedef Davranış (Başarı Kriteri)", custom["H1"]))
    story.append(
        P(
            "Normalize çıktısında tişört e-ticaret flat-lay standardında olmalıdır:",
            custom["Body"],
        )
    )
    for item in [
        "Yaka (neckline) her zaman görselin <b>üst-orta</b> (Kuzey) bölgesinde.",
        "Etek ucu (hem) her zaman <b>altta</b> (Güney); düz veya hafif kavisli dolu sınır.",
        "Kollar <b>sol ve sağda</b>; gövde dikey eksene paralel (sürekli açı ile düzeltilmiş).",
        "Askı / metal çubuk maskede yok; kırışıklık baskılanmış ama kumaş dokusu yok olmamış.",
        "Giriş fotoğrafı 30°/45°/60° çapraz, 90° yatay veya 180° ters olsa bile aynı kanonik sonuca inmeli.",
    ]:
        story.append(P(f"• {item}", custom["Bullet"]))

    # --- 3. Teknik arka plan ---
    story.append(P("3. Teknik Arka Plan: Neden Bu Kadar Zor?", custom["H1"]))
    story.append(P("3.1 Pipeline’daki yer", custom["H2"]))
    story.append(
        P(
            "Akış kabaca: <b>cutout</b> (rembg/chroma) → <b>refine alpha</b> → <b>polish</b> "
            "(aski → align_garment_upright → catalog press) → <b>compose_studio</b> (3:4 çerçeve). "
            "Orientation yalnızca polish içinde yaşar; framing sadece bbox’ı ortalar — yanlış "
            "yönü “düzeltemez”, yalnızca yanlış yönlü giysiyi güzelce ortalar.",
            custom["Body"],
        )
    )
    story.append(P("3.2 Bilgisayarla görmede “yön” iki ayrı problem", custom["H2"]))
    story.append(
        P(
            "<b>(A) Sürekli açı (deskew):</b> Silüet eksenini görüntü eksenlerine paralel yapmak "
            "(ör. −42.5° → 0°). Bu, minAreaRect / PCA ile çözülebilir.<br/><br/>"
            "<b>(B) Diskret yön (0/90/180/270):</b> Hangi taraf “yukarı”? Bu, etiketli bir "
            "sınıflandırma problemidir. Bounding box veya uzun kenar tek başına cevap vermez; "
            "çünkü T-shirt’te <b>kol–kol mesafesi</b> çoğu zaman <b>yaka–etek mesafesinden</b> "
            "uzundur. Algoritma “en uzun kenarı dikey yap” derse, kollar kuzey–güneye, yaka ise "
            "doğuya/batıya gider — tam da ekran görüntüsünde gördüğümüz durum.",
            custom["Body"],
        )
    )
    story.append(
        P(
            "Bu yüzden “açıyı kusursuz yakaladık ama tişört yatay” şikâyeti aslında başarı + "
            "başarısızlığın birleşimidir: (A) çözülmüş, (B) çözülmemiş.",
            custom["Callout"],
        )
    )

    story.append(P("3.3 Geometrik belirsizlik (ambiguity)", custom["H2"]))
    story.append(
        P(
            "Aynı silüet kenarı farklı yorumlara açıktır:",
            custom["Body"],
        )
    )
    for item in [
        "<b>Yaka çukuru:</b> Orta derin, yanlarda omuz — içbükey notch.",
        "<b>Etek:</b> Geniş, dolu, düşük varyans — düz sınır.",
        "<b>Kol açıklığı (yan görünüm):</b> Bbox’ın uzun kenarında gövde içeri çekik, "
        "kol uçları dışarıda → derinlik profili “sahte yaka çukuru” üretir.",
        "<b>Koltuk altı / etek kıvrımı:</b> Yerel çukurlar yaka ile karışır.",
        "<b>rembg hatası:</b> Yaka deliği kapanmış veya etek delinmiş olabilir; skor bozulur.",
    ]:
        story.append(P(f"• {item}", custom["Bullet"]))

    # --- 4. Detaylı kronoloji ---
    story.append(PageBreak())
    story.append(P("4. Detaylı Kronoloji: Ne Yaşadık?", custom["H1"]))

    story.append(P("4.1 Askı temizliği ve katalog press (başarılı katman)", custom["H2"]))
    story.append(
        P(
            "İlk polish adımlarında askı kancası morfolojik opening + omuz satırı budaması ile "
            "silindi; kırışıklık için bilateral / frequency separation uygulandı. Kullanıcı "
            "geri bildirimi: <b>askı başarıyla gitti</b>. Yani cutout + hanger + press hattı "
            "çalışıyor; şikâyet artık yalnızca orientation.",
            custom["Body"],
        )
    )

    story.append(P("4.2 PCA ±35° — neden yetersiz kaldı?", custom["H2"]))
    story.append(
        P(
            "İlk deskew, ana bileşen analizini (PCA) veya minAreaRect açısını <b>±35°</b> ile "
            "sınırladı. Gerekçe: aşırı dönüşten kaçınmak. Sonuç: 45°/90° çapraz veya yan yatmış "
            "tişörtlerde algoritma “zaten yeterince dikeyim” sanıp durdu; yaka sağda/altta kaldı. "
            "<b>Öğrenilen ders:</b> Sürekli açı ile kardinal (90°) yön aynı problem değildir; "
            "dar clamp 90° fazını matematiksel olarak çözemez.",
            custom["Body"],
        )
    )

    story.append(P("4.3 Dört adaylı 90° arama — etek/yaka karışması", custom["H2"]))
    story.append(
        P(
            "Sonra 0/90/180/270 adaylarını skorlayıp en iyisini seçtik. Skor “kenardaki çukur "
            "derinliği”ne dayanıyordu. Birkaç tur sonra ters örnekler görüldü: <b>etek yukarı</b>. "
            "Kök neden: boş sütunlar etek “düzlük” metriğini bozuyordu; ayrıca tek çukur skoru "
            "etek kıvrımı veya koltuk altını yaka sanabiliyordu. Düzeltme: tip_center (orta doluluk) "
            "vs tip_side (omuz doluluk) + hem likeness. Sentetik testlerde düzeldi; gerçek "
            "rembg maskelerinde hâlâ kırılgan.",
            custom["Body"],
        )
    )

    story.append(P("4.4 Continuous deskew — 90° fazın “mükemmel” görünmesi", custom["H2"]))
    story.append(
        P(
            "Kullanıcı talebi: kardinal aramayı kaldır, float açı ile tek warpAffine yap. "
            "Bu, 30°/45°/60° çaprazlar için birim testlerde mükemmel çalıştı (artık residual ≈ 0°). "
            "Ama üretimde yeni semptom doğdu: <b>eksenler dümdüz, tişört yatay, yaka sağda</b>.",
            custom["Body"],
        )
    )
    story.append(
        P(
            "Teknik açıklama: minAreaRect “uzun kenarı dikeye getir” politikası kullandığında, "
            "kol açıklığı gövde boyundan büyük olan T-shirt’lerde <b>uzun eksen = kol–kol</b> olur. "
            "Algoritma kolları dikey hizalar; yaka–etek ekseni yatay kalır. Bu bir bug değil, "
            "<b>yanlış hedef fonksiyonudur</b>: “eksen hizala” ≠ “yaka yukarı”.",
            custom["Callout"],
        )
    )

    story.append(P("4.5 Axis-snap + yaka→Kuzey — tasarım doğru, uygulama kırılgan", custom["H2"]))
    story.append(
        P(
            "Bir sonraki düzeltmede deskew’i “uzun kenarı dikeye zorlama”dan çıkarıp yalnızca "
            "≤45° eksen snap yaptık; ardından 4 kenardan yaka çukurunu bulup 0/90/180/270 ile "
            "Kuzey’e getirmeyi hedefledik. Birim testlerde (sentetik U-yaka tişört + 270° yan "
            "yatırma) Doğu kenarı seçilip 90° CCW uygulanıyor ve testler geçiyor (21 passed).",
            custom["Body"],
        )
    )
    story.append(
        P(
            "Buna rağmen kullanıcı hâlâ yatay çıktı görüyor. Bu, şu olasılıkları işaret eder:",
            custom["Body"],
        )
    )
    for item in [
        "<b>Skor seçimi:</b> Gerçek rembg maskesinde Doğu kenarı en yüksek yaka skorunu "
        "almıyor (yanlış kenar “top” seçiliyor → 0° dönüş → yatay kilit).",
        "<b>Uygulama yolu:</b> Karar doğru olsa bile warpAffine ile 90° dönüş, cv2.rotate’dan "
        "farklı davranış / canvas expand farkı üretmiş olabilir (son hotfix’te ROTATE_90_COUNTERCLOCKWISE zorunlu kılındı).",
        "<b>Servis sürümü:</b> Uvicorn hot-reload olmadan eski kod çalışıyor olabilir; logda "
        "“Orientation scores” / “Cardinal rotate applied” yoksa yeni kod yüklenmemiştir.",
        "<b>Çift işlem:</b> Backend normalize çağrısı eski Vision’a gidiyor veya cache’li "
        "orijinal URL gösteriliyor olabilir (orientation Vision’da düzelir, istemci eski asset’i görür).",
        "<b>Maske kalitesi:</b> Yaka deliği rembg’de kapanmışsa geometrik “çukur” kaybolur; "
        "omuz genişliği etek gibi skorlanır.",
    ]:
        story.append(P(f"• {item}", custom["Bullet"]))

    # --- 5. Kök neden modeli ---
    story.append(PageBreak())
    story.append(P("5. Kök Neden Modeli (Neyi Çözmekte Sıkıntı Yaşıyoruz?)", custom["H1"]))
    story.append(
        P(
            "Sıkıntı “OpenCV bilmiyoruz” değil. Sıkıntı şu üç belirsizliğin aynı anda var olması:",
            custom["Body"],
        )
    )
    story.append(P("5.1 Hedef eksen belirsizliği", custom["H2"]))
    story.append(
        P(
            "Hangi eksen “doğru dikey”? Kol–kol mu, yaka–etek mi? Klasik minAreaRect / PCA "
            "cevabı “en uzun varyans ekseni”dir; T-shirt için bu sıkça kollardır. "
            "<b>Çözüm ihtiyacı:</b> Deskew yalnızca eğimi kaldırmalı (axis-snap); portre/yön "
            "kararı ayrı, semantik (yaka) olmalı.",
            custom["Body"],
        )
    )
    story.append(P("5.2 Semantik kenar belirsizliği", custom["H2"]))
    story.append(
        P(
            "Yaka, etek ve kol kenarları 2D silüette benzer istatistikler üretebilir. "
            "Özellikle düşük kontrastlı beyaz tişört + kırışık + rembg sızıntısı olduğunda "
            "“orta çukur” güvenilir bir etiket olmaktan çıkar. "
            "<b>Çözüm ihtiyacı:</b> Daha güçlü özellikler (omuz çift tepe + orta boşluk + "
            "karşı kenarda düz etek) ve/veya öğrenilmiş bir yön sınıflandırıcı.",
            custom["Body"],
        )
    )
    story.append(P("5.3 Karar–eylem kopukluğu", custom["H2"]))
    story.append(
        P(
            "Debug döngüsünde en pahalı kısım: “algoritma doğru kenarı seçti mi?” ile "
            "“90° gerçekten uygulandı mı?” sorularının log olmadan ayırt edilememesi. "
            "Bu yüzden son turda Orientation scores log’u ve cv2.rotate zorunluluğu eklendi. "
            "Hâlâ semptom varsa bir sonraki adım log kanıtıyla skor mu yoksa runtime mı "
            "ayırt etmektir.",
            custom["Body"],
        )
    )

    # --- 6. Ne işe yaradı / yaramadı ---
    story.append(P("6. Ne İşe Yaradı, Ne Yaramadı?", custom["H1"]))
    rows2 = [
        [
            P("Yaklaşım", custom["TableHead"]),
            P("Sonuç", custom["TableHead"]),
            P("Neden", custom["TableHead"]),
        ],
        [
            P("Hanger morph + LCC", custom["TableCell"]),
            P("İşe yaradı", custom["TableCell"]),
            P("Askı ince, lokal; silüetten ayırt edilebilir", custom["TableCell"]),
        ],
        [
            P("Frequency separation press", custom["TableCell"]),
            P("Kısmen", custom["TableCell"]),
            P("Kırışıklığı azaltır; yönü düzeltmez", custom["TableCell"]),
        ],
        [
            P("PCA clamp ±35°", custom["TableCell"]),
            P("Yaramadı (90° için)", custom["TableCell"]),
            P("Kardinal faz clamp dışında", custom["TableCell"]),
        ],
        [
            P("Tek çukur skoru", custom["TableCell"]),
            P("Kırılgan", custom["TableCell"]),
            P("Etek/koltuk/sahte yan çukur karışır", custom["TableCell"]),
        ],
        [
            P("Long-axis → vertical", custom["TableCell"]),
            P("Zararlı (T-shirt)", custom["TableCell"]),
            P("Kol eksenini gövde sanır → 90° faz", custom["TableCell"]),
        ],
        [
            P("Axis-snap ≤45° + yaka kardinal", custom["TableCell"]),
            P("Doğru mimari", custom["TableCell"]),
            P("A/B ayrımı doğru; skor/runtime hâlâ kritik", custom["TableCell"]),
        ],
        [
            P("Sentetik unit testler", custom["TableCell"]),
            P("Yeşil ama yetersiz", custom["TableCell"]),
            P("Temiz U-yaka; gerçek rembg gürültüsünü temsil etmiyor", custom["TableCell"]),
        ],
    ]
    t2 = Table(rows2, colWidths=[4.2 * cm, 3.2 * cm, 8.8 * cm])
    t2.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
                ("BACKGROUND", (0, 1), (-1, -1), BG_SOFT),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("GRID", (0, 0), (-1, -1), 0.4, RULE),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    story.append(t2)

    # --- 7. Kanıt / log ---
    story.append(P("7. Tanı Protokolü (Bundan Sonra Ne Bakılacak?)", custom["H1"]))
    story.append(
        P(
            "Vision terminalinde şu satırlar bir normalize isteğinde görünmelidir:",
            custom["Body"],
        )
    )
    story.append(
        P(
            "1) <font face='Courier'>Orientation scores: {'best': 'right', 'combined': {...}, ...}</font><br/>"
            "2) <font face='Courier'>Cardinal rotate applied: edge=right deg_ccw=90</font><br/>"
            "3) <font face='Courier'>Orientation verify OK: neck=top</font> "
            "veya WARNING (bbox yatay / yaka hâlâ Kuzey değil)",
            custom["Mono"],
        )
    )
    story.append(
        P(
            "Yorumlama:<br/>"
            "• Log yoksa → süreç eski kodu çalıştırıyor (restart şart).<br/>"
            "• best=top ama görüntü yataysa → skor yanılıyor (özellik mühendisliği / maske).<br/>"
            "• best=right ama Cardinal rotate yoksa → eylem kopukluğu (bug).<br/>"
            "• Rotate var, verify WARNING → dönüş yanlış yönde veya ikinci bir adım bozuyor.",
            custom["Body"],
        )
    )

    # --- 8. Önerilen yol ---
    story.append(P("8. Kalıcı Çözüm İçin Önerilen Yol Haritası", custom["H1"]))
    story.append(P("Kısa vade (1–2 gün)", custom["H2"]))
    for item in [
        "Gerçek başarısız fotoğraflardan 10–20 maske kaydet; golden-file regression testleri yaz.",
        "Her normalize’da Orientation scores + rotate satırını zorunlu logla; Flutter’da yeni URL doğrula.",
        "Kararı sadeleştir: yalnızca kısa kenar adaylarında (iki uç) yaka/etek yarıştı; uzun kenarları cezalandır.",
        "Yaka seçildikten sonra mutlaka cv2.ROTATE_*; warpAffine’i kardinal için kullanma.",
    ]:
        story.append(P(f"• {item}", custom["Bullet"]))

    story.append(P("Orta vade", custom["H2"]))
    for item in [
        "Küçük bir yön sınıflandırıcı: 4 sınıf (0/90/180/270), girdi = alpha mask thumbnail.",
        "Omuz çizgisi / iki tepe tespiti (1D profilde bimodal omuz + orta çukur).",
        "İnsan-in-the-loop: düşük confidence’ta UI’da döndürme düğmesi (MVP).",
    ]:
        story.append(P(f"• {item}", custom["Bullet"]))

    story.append(P("Uzun vade", custom["H2"]))
    story.append(
        P(
            "Garment parsing (SCHP / DensePose benzeri) ile “neck / hem / sleeve” etiketleri "
            "doğrudan maskeden gelsin; geometrik sezgilere daha az bağımlı olunsun. "
            "Bu, VTON tarafındaki SCHP yatırımıyla da hizalanır.",
            custom["Body"],
        )
    )

    # --- 9. Sonuç ---
    story.append(P("9. Sonuç", custom["H1"]))
    story.append(
        P(
            "Son birkaç düzeltmede yaşadığımız asıl sorun, “açıyı bulamamak” değildi. "
            "Asıl sorun, T-shirt silüetinde <b>hangi eksenin ve hangi kenarın “yukarı”</b> "
            "anlamına geldiğini tek bir robust kurala bağlayamamamızdı. Continuous deskew "
            "çapraz çekimleri düzeltti; uzun-kenar politikası ise yeni bir 90° faz hatası üretti. "
            "Yaka skoru + kardinal rotasyon doğru mimari ayrım; fakat gerçek maskelerde skor "
            "güvenilirliği ve runtime’da dönüşün kanıtlanabilir uygulanması hâlâ açık savaş alanı.",
            custom["Body"],
        )
    )
    story.append(
        P(
            "Pratik olarak: askı/press çözüldü; deskew çözüldü; <b>orientation semantics</b> "
            "çözülüyor. Bir sonraki iterasyonun başarısı log kanıtı + gerçek golden set olmadan "
            "ölçülemez.",
            custom["Callout"],
        )
    )

    story.append(Spacer(1, 0.8 * cm))
    story.append(HRFlowable(width="100%", thickness=0.8, color=RULE, spaceAfter=8))
    story.append(
        P(
            "Aura Engineering — Garment Studio Orientation Sorun Analizi — Gizli / Dahili",
            custom["Footer"],
        )
    )

    doc.build(story)
    return OUT


if __name__ == "__main__":
    path = build()
    print(path)
