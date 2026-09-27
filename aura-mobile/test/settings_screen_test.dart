import 'package:aura_mobile/main.dart';
import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/models/outfit_favorite.dart';
import 'package:aura_mobile/models/user_perfume.dart';
import 'package:aura_mobile/models/vton_lookbook_entry.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/screens/auth_gate.dart';
import 'package:aura_mobile/screens/settings_screen.dart';
import 'package:aura_mobile/services/api_service.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('SettingsScreen hesap ve cikis butonunu gosterir', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: SettingsScreen(),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Ayarlar'), findsOneWidget);
    expect(find.text('Hesap'), findsOneWidget);
    expect(find.text('Misafir'), findsOneWidget);
    expect(find.text('Çıkış Yap'), findsOneWidget);
  });

  testWidgets('cikis onaylaninca oturum kapanir ve giris ekrani gelir', (tester) async {
    final store = MemoryAuthSecureStore();
    await store.save(
      const AuthSession(
        accessToken: 'test-access',
        refreshToken: 'test-refresh',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 1,
        email: 'test@aura.app',
        username: 'test',
      ),
    );
    var logoutCalls = 0;
    final client = MockClient((request) async {
      if (request.method == 'POST' && request.url.path.endsWith('/auth/logout')) {
        logoutCalls++;
        return http.Response('', 204);
      }
      return http.Response('[]', 200);
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authSecureStoreProvider.overrideWithValue(store),
          apiServiceProvider.overrideWithValue(
            ApiService(
              client: client,
              backendBaseUrl: 'http://backend.test',
              visionBaseUrl: 'http://vision.test',
              accessTokenProvider: () => 'test-access',
            ),
          ),
          wardrobeProvider.overrideWith(_StubWardrobe.new),
          favoritesProvider.overrideWith(_StubFavorites.new),
          lookbookProvider.overrideWith(_StubLookbook.new),
          perfumeShelfProvider.overrideWith(_StubShelf.new),
          perfumeCatalogProvider.overrideWith(_StubCatalog.new),
        ],
        child: const AuraApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Raf'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();

    expect(find.text('test@aura.app'), findsOneWidget);

    await tester.tap(find.widgetWithText(OutlinedButton, 'Çıkış Yap'));
    await tester.pumpAndSettle();
    expect(find.text('Vazgeç'), findsOneWidget);

    await tester.tap(find.widgetWithText(TextButton, 'Çıkış Yap'));
    await tester.pumpAndSettle();

    final container = ProviderScope.containerOf(tester.element(find.byType(AuthGate)));
    expect(container.read(authSessionProvider), isNull);
    expect(await store.read(), isNull);
    expect(logoutCalls, 1);
    expect(find.text('Giris Yap'), findsOneWidget);
    expect(find.text('Ayarlar'), findsNothing);
  });
}

class _StubWardrobe extends WardrobeNotifier {
  @override
  Future<List<WardrobeItem>> build() async => const [];
}

class _StubFavorites extends FavoritesNotifier {
  @override
  Future<List<OutfitFavorite>> build() async => const [];
}

class _StubLookbook extends LookbookNotifier {
  @override
  Future<List<VtonLookbookEntry>> build() async => const [];
}

class _StubShelf extends PerfumeShelfNotifier {
  @override
  Future<List<UserPerfume>> build() async => const [];
}

class _StubCatalog extends PerfumeCatalogNotifier {
  @override
  Future<List<UserPerfume>> build() async => const [];
}
