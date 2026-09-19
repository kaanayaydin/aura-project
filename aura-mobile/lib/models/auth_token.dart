/// Oturum modeli — access + refresh (veya demo access-only).
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.tokenType,
    required this.expiresInSeconds,
    required this.userId,
    this.refreshToken,
    this.email = '',
    this.username = '',
    this.displayName,
    this.isDemo = false,
  });

  final String accessToken;
  final String? refreshToken;
  final String tokenType;
  final int expiresInSeconds;
  final int userId;
  final String email;
  final String username;
  final String? displayName;
  final bool isDemo;

  String get authorizationHeader => '$tokenType $accessToken';

  bool get hasRefreshToken =>
      refreshToken != null && refreshToken!.isNotEmpty;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    if (json.containsKey('expiresIn') || json.containsKey('refreshToken')) {
      return AuthSession.fromSessionJson(json);
    }
    return AuthSession.fromDemoJson(json);
  }

  /// Login / refresh cevabi (`expiresIn` saniye).
  factory AuthSession.fromSessionJson(
    Map<String, dynamic> json, {
    String? displayName,
  }) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String?,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInSeconds: (json['expiresIn'] as num?)?.toInt() ?? 900,
      userId: (json['userId'] as num).toInt(),
      email: json['email'] as String? ?? '',
      username: json['username'] as String? ?? '',
      displayName: displayName,
      isDemo: false,
    );
  }

  /// Deprecated demo `/auth/token` cevabi.
  factory AuthSession.fromDemoJson(Map<String, dynamic> json) {
    final minutes = (json['expiresInMinutes'] as num?)?.toInt() ?? 15;
    return AuthSession(
      accessToken: json['accessToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInSeconds: minutes * 60,
      userId: (json['userId'] as num).toInt(),
      username: json['username'] as String? ?? 'demo',
      email: '',
      isDemo: true,
    );
  }

  AuthSession copyWith({
    String? accessToken,
    String? refreshToken,
    String? tokenType,
    int? expiresInSeconds,
    int? userId,
    String? email,
    String? username,
    String? displayName,
    bool? isDemo,
  }) {
    return AuthSession(
      accessToken: accessToken ?? this.accessToken,
      refreshToken: refreshToken ?? this.refreshToken,
      tokenType: tokenType ?? this.tokenType,
      expiresInSeconds: expiresInSeconds ?? this.expiresInSeconds,
      userId: userId ?? this.userId,
      email: email ?? this.email,
      username: username ?? this.username,
      displayName: displayName ?? this.displayName,
      isDemo: isDemo ?? this.isDemo,
    );
  }

  /// Secure storage icin tek-kayit JSON (Keychain prompt sayisini dusurur).
  Map<String, dynamic> toPersistJson() => {
        'accessToken': accessToken,
        'refreshToken': refreshToken,
        'tokenType': tokenType,
        'expiresIn': expiresInSeconds,
        'userId': userId,
        'email': email,
        'username': username,
        'displayName': displayName,
        'isDemo': isDemo,
      };

  factory AuthSession.fromPersistJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String? ?? '',
      refreshToken: json['refreshToken'] as String?,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInSeconds: (json['expiresIn'] as num?)?.toInt() ?? 900,
      userId: (json['userId'] as num?)?.toInt() ?? 0,
      email: json['email'] as String? ?? '',
      username: json['username'] as String? ?? '',
      displayName: json['displayName'] as String?,
      isDemo: json['isDemo'] as bool? ?? false,
    );
  }
}

/// Geriye uyumluluk — eski AuthToken adi.
typedef AuthToken = AuthSession;
