/// Aura mobil istemci yapilandirmasi.
///
/// iOS simulator: 127.0.0.1
/// Android emulator: 10.0.2.2
/// Fiziksel cihaz: makinenizin LAN IP'sini --dart-define ile verin.
library;

import 'dart:io' show Platform;

class AuraConfig {
  AuraConfig._();

  static const String _backendOverride = String.fromEnvironment('AURA_BACKEND_URL');
  static const String _visionOverride = String.fromEnvironment('AURA_VISION_URL');
  /// true ise sifresiz demo /auth/token fallback kullanilir.
  static const bool useDemoAuth =
      bool.fromEnvironment('USE_DEMO_AUTH', defaultValue: false);

  static String get backendBaseUrl {
    if (_backendOverride.isNotEmpty) return _backendOverride;
    return 'http://$_loopbackHost:8080';
  }

  static String get visionBaseUrl {
    if (_visionOverride.isNotEmpty) return _visionOverride;
    return 'http://$_loopbackHost:8000';
  }

  static String get _loopbackHost {
    if (Platform.isAndroid) return '10.0.2.2';
    return '127.0.0.1';
  }
}
