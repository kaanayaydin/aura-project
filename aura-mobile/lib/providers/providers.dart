import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../core/config.dart';
import '../models/auth_token.dart';
import '../models/chat_message.dart';
import '../models/outfit_favorite.dart';
import '../models/suggestion.dart';
import '../models/user_perfume.dart';
import '../models/vton_lookbook_entry.dart';
import '../models/wardrobe_item.dart';
import '../services/api_service.dart';
import '../services/auth_secure_store.dart';
import '../services/person_photo_service.dart';

final authSecureStoreProvider = Provider<AuthSecureStore>((ref) {
  return FlutterAuthSecureStore();
});

/// JWT bellegi — secure storage + memory; ApiService ile dongusel bagimlilik yok.
final authSessionProvider =
    NotifierProvider<AuthSessionNotifier, AuthSession?>(AuthSessionNotifier.new);

/// true iken AuthGate hydrate tamamlanmisti.
final authHydratedProvider = StateProvider<bool>((ref) => false);

class AuthSessionNotifier extends Notifier<AuthSession?> {
  @override
  AuthSession? build() => null;

  AuthSecureStore get _store => ref.read(authSecureStoreProvider);

  Future<void> hydrate() async {
    try {
      final saved = await _store.read();
      state = saved;
    } catch (_) {
      state = null;
    } finally {
      ref.read(authHydratedProvider.notifier).state = true;
    }
  }

  Future<void> applySession(AuthSession session) async {
    state = session;
    try {
      await _store.save(session);
    } catch (_) {
      // Keychain yazilamasa bellek oturumu yine gecerli
    }
  }

  Future<void> clearLocal() async {
    state = null;
    try {
      await _store.clear();
    } catch (_) {}
  }

  /// Demo veya mevcut oturum. Gercek auth'ta oturum yoksa hata.
  Future<AuthSession> ensure({int? userId, String? username}) async {
    final current = state;
    if (current != null && userId == null && username == null) {
      return current;
    }
    if (!AuraConfig.useDemoAuth && userId == null && username == null) {
      if (current != null) return current;
      throw ApiException(401, 'Oturum gerekli. Lutfen giris yapin.');
    }
    final token = await ref.read(apiServiceProvider).fetchAuthToken(
          userId: userId,
          username: username,
        );
    await applySession(token);
    return token;
  }

  Future<AuthSession> login({
    required String email,
    required String password,
    String? displayName,
  }) async {
    final session = await ref.read(apiServiceProvider).login(
          email: email,
          password: password,
        );
    final withName = displayName == null || displayName.isEmpty
        ? session
        : session.copyWith(displayName: displayName);
    await applySession(withName);
    return withName;
  }

  Future<AuthSession> register({
    required String email,
    required String password,
    String? displayName,
  }) async {
    final api = ref.read(apiServiceProvider);
    await api.register(email: email, password: password);
    return login(email: email, password: password, displayName: displayName);
  }

  Future<void> logout() async {
    final session = state;
    final refresh = session?.refreshToken;
    if (session != null && refresh != null && refresh.isNotEmpty) {
      try {
        await ref.read(apiServiceProvider).logout(refreshToken: refresh);
      } catch (_) {}
    }
    await clearLocal();
  }

  void clear() {
    // Senkron temizleme (interceptor); persist arka planda
    state = null;
    _store.clear();
  }
}

final apiServiceProvider = Provider<ApiService>((ref) {
  return ApiService(
    accessTokenProvider: () => ref.read(authSessionProvider)?.accessToken,
    refreshTokenProvider: () => ref.read(authSessionProvider)?.refreshToken,
    onSessionUpdated: (session) async {
      final previous = ref.read(authSessionProvider);
      final merged = previous == null
          ? session
          : session.copyWith(displayName: previous.displayName);
      await ref.read(authSessionProvider.notifier).applySession(merged);
    },
    onSessionExpired: () async {
      await ref.read(authSessionProvider.notifier).clearLocal();
    },
  );
});

final personPhotoServiceProvider =
    Provider<PersonPhotoService>((ref) => PersonPhotoService());

final wardrobeProvider =
    AsyncNotifierProvider<WardrobeNotifier, List<WardrobeItem>>(
  WardrobeNotifier.new,
);

class WardrobeNotifier extends AsyncNotifier<List<WardrobeItem>> {
  @override
  Future<List<WardrobeItem>> build() async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
    } catch (_) {
      return const [];
    }
    return _load();
  }

  ApiService get _api => ref.read(apiServiceProvider);

  Future<List<WardrobeItem>> _load() => _api.fetchWardrobe(includeImages: true);

  /// Sessiz yenileme: onceki listeyi ekranda tutarak ceker.
  Future<void> refresh({bool silent = false}) async {
    if (!silent) {
      state = const AsyncLoading();
    }
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return _load();
    });
  }

  /// Galeri/kamera → Vision /analyze (kategori) → tek POST wardrobe (backend normalize).
  ///
  /// Cift kayit onlenir: analyze'a JWT gitmez (Vision sync yazmaz);
  /// istemci MinIO/normalize yapmaz — tek createWardrobeItem + imageBase64.
  Future<String> uploadFromSource(ImageSource source) async {
    final picker = ImagePicker();
    final file = await picker.pickImage(
      source: source,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (file == null) {
      return 'Iptal edildi.';
    }

    final bytes = await file.readAsBytes();

    await ref.read(authSessionProvider.notifier).ensure();

    final analyze = await _api.analyzeImage(
      bytes: bytes,
      fileName: file.name,
      contentType: file.mimeType ?? 'image/jpeg',
      forwardAuth: false,
    );

    // En guvenilir tek aday (cutout varsa tercih)
    final candidates = <({String category, double? confidence})>[];
    for (final d in analyze.withCutouts) {
      final c = d.category;
      if (c == null || c.isEmpty) continue;
      candidates.add((category: c, confidence: d.categoryConfidence));
    }
    if (candidates.isEmpty) {
      for (final d in analyze.detectedCategories) {
        final c = d.category;
        if (c == null || c.isEmpty) continue;
        candidates.add((category: c, confidence: d.categoryConfidence));
      }
    }

    if (candidates.isEmpty) {
      await refresh(silent: true);
      return 'Vision analiz tamam ama kategori bulunamadi; dolaba yazilmadi.';
    }

    candidates.sort(
      (a, b) => (b.confidence ?? 0).compareTo(a.confidence ?? 0),
    );
    final best = candidates.first;

    // Tek kayit: ham gorsel → backend studio normalize + MinIO
    await _api.createWardrobeItem(
      category: best.category,
      categoryConfidence: best.confidence,
      imageBase64: base64Encode(bytes),
    );

    // ignore: avoid_print
    print(
      '[Aura] Tek dolap kaydi: category=${best.category} '
      'conf=${best.confidence} bytes=${bytes.length}',
    );

    await refresh(silent: true);
    return 'Analiz tamam (${best.category}). Stüdyo normalize dolaba kaydedildi.';
  }
}

final suggestionProvider =
    AsyncNotifierProvider<SuggestionNotifier, SuggestionResponse?>(
  SuggestionNotifier.new,
);

class SuggestionNotifier extends AsyncNotifier<SuggestionResponse?> {
  @override
  Future<SuggestionResponse?> build() async => null;

  Future<void> request({
    required double temperature,
    required double humidity,
    required OccasionOption occasion,
    bool useAutoWeather = false,
    double? latitude,
    double? longitude,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return ref.read(apiServiceProvider).suggest(
            temperatureCelsius: useAutoWeather ? null : temperature,
            humidityPercent: useAutoWeather ? null : humidity,
            occasion: occasion.apiValue,
            includeImages: true,
            useAutoWeather: useAutoWeather,
            latitude: latitude,
            longitude: longitude,
          );
    });
  }

  void clear() {
    state = const AsyncData(null);
  }
}

final perfumeCatalogProvider =
    AsyncNotifierProvider<PerfumeCatalogNotifier, List<UserPerfume>>(
  PerfumeCatalogNotifier.new,
);

class PerfumeCatalogNotifier extends AsyncNotifier<List<UserPerfume>> {
  @override
  Future<List<UserPerfume>> build() async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
    } catch (_) {
      return const [];
    }
    return ref.read(apiServiceProvider).fetchPerfumeCatalog();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return ref.read(apiServiceProvider).fetchPerfumeCatalog();
    });
  }

  Future<void> addToShelf(String catalogId) async {
    await ref.read(authSessionProvider.notifier).ensure();
    await ref.read(apiServiceProvider).addPerfumeToShelf(catalogId);
    await refresh();
    await ref.read(perfumeShelfProvider.notifier).refresh();
  }
}

final perfumeShelfProvider =
    AsyncNotifierProvider<PerfumeShelfNotifier, List<UserPerfume>>(
  PerfumeShelfNotifier.new,
);

class PerfumeShelfNotifier extends AsyncNotifier<List<UserPerfume>> {
  @override
  Future<List<UserPerfume>> build() async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
    } catch (_) {
      return const [];
    }
    return ref.read(apiServiceProvider).fetchPerfumeShelf();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return ref.read(apiServiceProvider).fetchPerfumeShelf();
    });
  }

  Future<void> remove(int id) async {
    await ref.read(authSessionProvider.notifier).ensure();
    await ref.read(apiServiceProvider).removePerfumeFromShelf(id);
    await refresh();
    await ref.read(perfumeCatalogProvider.notifier).refresh();
  }
}

final favoritesProvider =
    AsyncNotifierProvider<FavoritesNotifier, List<OutfitFavorite>>(
  FavoritesNotifier.new,
);

final lookbookProvider =
    AsyncNotifierProvider<LookbookNotifier, List<VtonLookbookEntry>>(
  LookbookNotifier.new,
);

class LookbookNotifier extends AsyncNotifier<List<VtonLookbookEntry>> {
  @override
  Future<List<VtonLookbookEntry>> build() async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
    } catch (_) {
      return const [];
    }
    return ref.read(apiServiceProvider).fetchLookbook();
  }

  Future<void> refresh({bool silent = false}) async {
    if (!silent) {
      state = const AsyncLoading();
    }
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return ref.read(apiServiceProvider).fetchLookbook();
    });
  }

  Future<VtonLookbookEntry> save({required int jobId, int? userId}) async {
    await ref.read(authSessionProvider.notifier).ensure(userId: userId);
    final entry = await ref.read(apiServiceProvider).saveToLookbook(
          jobId: jobId,
          userId: userId,
        );
    await refresh(silent: true);
    return entry;
  }
}

class FavoritesNotifier extends AsyncNotifier<List<OutfitFavorite>> {
  @override
  Future<List<OutfitFavorite>> build() async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
    } catch (_) {
      return const [];
    }
    return ref.read(apiServiceProvider).fetchFavorites();
  }

  Future<void> refresh({bool silent = false}) async {
    if (!silent) {
      state = const AsyncLoading();
    }
    state = await AsyncValue.guard(() async {
      await ref.read(authSessionProvider.notifier).ensure();
      return ref.read(apiServiceProvider).fetchFavorites();
    });
  }

  Future<void> remove(int id) async {
    final previous = state.valueOrNull;
    if (previous != null) {
      state = AsyncData(
        previous.where((favorite) => favorite.id != id).toList(),
      );
    }
    try {
      await ref.read(authSessionProvider.notifier).ensure();
      await ref.read(apiServiceProvider).deleteFavorite(id);
      await refresh(silent: true);
    } catch (error, stackTrace) {
      if (previous != null) {
        state = AsyncData(previous);
      } else {
        state = AsyncError(error, stackTrace);
      }
      rethrow;
    }
  }
}

/// Aura AI sohbet oturumu (bellekte; uygulama acik kaldikca).
final chatProvider = NotifierProvider<ChatNotifier, ChatState>(ChatNotifier.new);

class ChatState {
  const ChatState({
    this.messages = const [],
    this.sending = false,
    this.lastSource,
    this.lastWeatherSummary,
    this.error,
  });

  final List<ChatMessage> messages;
  final bool sending;
  final String? lastSource;
  final String? lastWeatherSummary;
  final String? error;

  ChatState copyWith({
    List<ChatMessage>? messages,
    bool? sending,
    String? lastSource,
    String? lastWeatherSummary,
    String? error,
    bool clearError = false,
  }) {
    return ChatState(
      messages: messages ?? this.messages,
      sending: sending ?? this.sending,
      lastSource: lastSource ?? this.lastSource,
      lastWeatherSummary: lastWeatherSummary ?? this.lastWeatherSummary,
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class ChatNotifier extends Notifier<ChatState> {
  static const _defaultLat = 41.0082;
  static const _defaultLon = 28.9784;

  @override
  ChatState build() => const ChatState();

  Future<void> send(String raw) async {
    final text = raw.trim();
    if (text.isEmpty || state.sending) return;

    final history = state.messages
        .where((m) => m.role == 'user' || m.role == 'assistant')
        .toList();

    final userMessage = ChatMessage(role: 'user', content: text);
    state = state.copyWith(
      messages: [...state.messages, userMessage],
      sending: true,
      clearError: true,
    );

    try {
      await ref.read(authSessionProvider.notifier).ensure();
      final response = await ref.read(apiServiceProvider).chat(
            message: text,
            history: history,
            latitude: _defaultLat,
            longitude: _defaultLon,
          );
      final assistant = ChatMessage(
        role: 'assistant',
        content: response.reply,
        source: response.source,
        model: response.model,
      );
      state = state.copyWith(
        messages: [...state.messages, assistant],
        sending: false,
        lastSource: response.source,
        lastWeatherSummary: response.weatherSummary,
      );
    } catch (error) {
      final message = error is ApiException
          ? error.message
          : 'Aura AI yanit uretemedi. Backend / Ollama ayakta mi?';
      state = state.copyWith(sending: false, error: message);
    }
  }

  void clear() {
    state = const ChatState();
  }
}
