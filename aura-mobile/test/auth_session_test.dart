import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('MemoryAuthSecureStore round-trip', () async {
    final store = MemoryAuthSecureStore();
    expect(await store.read(), isNull);

    const session = AuthSession(
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 42,
      email: 'a@b.co',
      displayName: 'Kaan',
    );
    await store.save(session);
    final loaded = await store.read();
    expect(loaded?.accessToken, 'access');
    expect(loaded?.refreshToken, 'refresh');
    expect(loaded?.userId, 42);
    expect(loaded?.displayName, 'Kaan');

    await store.clear();
    expect(await store.read(), isNull);
  });

  test('AuthSession persist JSON round-trip', () {
    const session = AuthSession(
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      userId: 7,
      email: 'x@y.z',
      username: 'kaan',
      displayName: 'Kaan',
      isDemo: false,
    );
    final again = AuthSession.fromPersistJson(session.toPersistJson());
    expect(again.accessToken, session.accessToken);
    expect(again.refreshToken, session.refreshToken);
    expect(again.userId, session.userId);
    expect(again.email, session.email);
    expect(again.displayName, session.displayName);
    expect(again.isDemo, isFalse);
  });
}
