import 'package:aura_mobile/main.dart';
import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/models/outfit_favorite.dart';
import 'package:aura_mobile/models/user_perfume.dart';
import 'package:aura_mobile/models/vton_lookbook_entry.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Aura app boots with Aura AI tab when authenticated', (tester) async {
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

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authSecureStoreProvider.overrideWithValue(store),
          wardrobeProvider.overrideWith(() => _StubWardrobe()),
          favoritesProvider.overrideWith(() => _StubFavorites()),
          lookbookProvider.overrideWith(() => _StubLookbook()),
          perfumeShelfProvider.overrideWith(() => _StubShelf()),
          perfumeCatalogProvider.overrideWith(() => _StubCatalog()),
        ],
        child: const AuraApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Aura'), findsWidgets);
    expect(find.text('Dolap'), findsOneWidget);
    expect(find.text('Oneri'), findsOneWidget);
    expect(find.text('Aura AI'), findsOneWidget);
    expect(find.text('Arşiv'), findsOneWidget);
    expect(find.text('Raf'), findsOneWidget);
  });

  testWidgets('Unauthenticated app shows login', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authSecureStoreProvider.overrideWithValue(MemoryAuthSecureStore()),
        ],
        child: const AuraApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Giris yap'), findsOneWidget);
    expect(find.text('Giris Yap'), findsOneWidget);
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
