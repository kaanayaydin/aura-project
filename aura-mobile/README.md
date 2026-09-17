# Aura Mobile

Flutter istemcisi — sanal dolap listesi, Vision ile fotograf analizi ve
Baglam/Termodinamik Motoru onerileri.

## Calistirma

Backend (8080) ve istege bagli Vision (8000) ayakta olmali:

```bash
# iOS simulator / macOS masaustu
cd aura-mobile
flutter run

# Android emulator (10.0.2.2 otomatik)
flutter run

# Fiziksel cihaz (LAN IP)
flutter run --dart-define=AURA_BACKEND_URL=http://192.168.1.10:8080 \
            --dart-define=AURA_VISION_URL=http://192.168.1.10:8000
```

## Ekranlar

- **Dolap:** `GET /api/v1/wardrobe/items?includeImages=true`
- **Fotograf ekle:** Vision `POST /api/v1/vision/analyze` → dolap senkronu / yedek yazma
- **Oneri:** `POST /api/v1/aura/suggest` (sicaklik, nem, occasion)
