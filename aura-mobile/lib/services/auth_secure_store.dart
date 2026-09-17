import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../models/auth_token.dart';

/// Access/refresh token icin guvenli depolama sozlesmesi.
abstract class AuthSecureStore {
  Future<void> save(AuthSession session);
  Future<AuthSession?> read();
  Future<void> clear();
}

class FlutterAuthSecureStore implements AuthSecureStore {
  FlutterAuthSecureStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              // macOS debug (ad-hoc imza): Data Protection Keychain + varsayilan
              // "flutter_secure_storage_service" ACL her okuma/yazmada sifre
              // dialogu dongusune yol acar. Tek servis adi + DP kapali +
              // first_unlock ile Keychain erisimini sakinlestiririz.
              mOptions: MacOsOptions(
                useDataProtectionKeyChain: false,
                accessibility: KeychainAccessibility.first_unlock,
                accountName: 'app.aura.auth',
                synchronizable: false,
              ),
              iOptions: IOSOptions(
                accessibility: KeychainAccessibility.first_unlock,
              ),
            );

  /// Tek Keychain kaydi — save/read basina 1 SecItem cagrisi.
  static const _kSession = 'aura_auth_session_v1';

  final FlutterSecureStorage _storage;

  @override
  Future<void> save(AuthSession session) async {
    await _storage.write(
      key: _kSession,
      value: jsonEncode(session.toPersistJson()),
    );
  }

  @override
  Future<AuthSession?> read() async {
    final raw = await _storage.read(key: _kSession);
    if (raw == null || raw.isEmpty) return null;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final session = AuthSession.fromPersistJson(map);
      if (session.accessToken.isEmpty || session.userId <= 0) return null;
      return session;
    } on FormatException {
      return null;
    } on TypeError {
      return null;
    }
  }

  @override
  Future<void> clear() async {
    await _storage.delete(key: _kSession);
  }
}

/// Test / widget icin bellek ici saklama (Keychain yok).
class MemoryAuthSecureStore implements AuthSecureStore {
  AuthSession? _session;

  @override
  Future<void> save(AuthSession session) async => _session = session;

  @override
  Future<AuthSession?> read() async => _session;

  @override
  Future<void> clear() async => _session = null;
}
