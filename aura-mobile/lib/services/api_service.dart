import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../core/config.dart';
import '../models/analyze_result.dart';
import '../models/auth_token.dart';
import '../models/chat_message.dart';
import '../models/outfit_favorite.dart';
import '../models/suggestion.dart';
import '../models/user_perfume.dart';
import '../models/vton_job.dart';
import '../models/vton_lookbook_entry.dart';
import '../models/wardrobe_item.dart';
import '../models/weather_snapshot.dart';

/// Backend ve Vision HTTP istemcisi — 401'de otomatik refresh + tek retry.
class ApiService {
  ApiService({
    http.Client? client,
    String? backendBaseUrl,
    String? visionBaseUrl,
    String? Function()? accessTokenProvider,
    String? Function()? refreshTokenProvider,
    Future<void> Function(AuthSession session)? onSessionUpdated,
    Future<void> Function()? onSessionExpired,
  })  : _client = client ?? http.Client(),
        backendBaseUrl = backendBaseUrl ?? AuraConfig.backendBaseUrl,
        visionBaseUrl = visionBaseUrl ?? AuraConfig.visionBaseUrl,
        _accessTokenProvider = accessTokenProvider,
        _refreshTokenProvider = refreshTokenProvider,
        _onSessionUpdated = onSessionUpdated,
        _onSessionExpired = onSessionExpired;

  final http.Client _client;
  final String backendBaseUrl;
  final String visionBaseUrl;
  final String? Function()? _accessTokenProvider;
  final String? Function()? _refreshTokenProvider;
  final Future<void> Function(AuthSession session)? _onSessionUpdated;
  final Future<void> Function()? _onSessionExpired;

  static const Duration _timeout = Duration(seconds: 60);

  String? get _bearer {
    final raw = _accessTokenProvider?.call();
    if (raw == null || raw.isEmpty) return null;
    return raw.startsWith('Bearer ') ? raw : 'Bearer $raw';
  }

  Map<String, String> _jsonHeaders({bool auth = false}) {
    return {
      'Content-Type': 'application/json',
      if (auth && _bearer != null) 'Authorization': _bearer!,
    };
  }

  Map<String, String> _authHeaders() {
    final token = _bearer;
    if (token == null) return const {};
    return {'Authorization': token};
  }

  Future<http.Response> _authGet(Uri uri, {Duration? timeout}) {
    return _withAuthRetry(
      () => _client.get(uri, headers: _authHeaders()).timeout(timeout ?? _timeout),
    );
  }

  Future<http.Response> _authPost(
    Uri uri, {
    Object? body,
    Duration? timeout,
  }) {
    return _withAuthRetry(
      () => _client
          .post(
            uri,
            headers: _jsonHeaders(auth: true),
            body: body,
          )
          .timeout(timeout ?? _timeout),
    );
  }

  Future<http.Response> _authDelete(Uri uri) {
    return _withAuthRetry(
      () => _client.delete(uri, headers: _authHeaders()).timeout(_timeout),
    );
  }

  /// 401 → refresh → istegi bir kez tekrarla; refresh de olmazsa oturumu dusur.
  Future<http.Response> _withAuthRetry(
    Future<http.Response> Function() send,
  ) async {
    var response = await send();
    if (response.statusCode != 401) return response;

    final refreshed = await tryRefreshAccessToken();
    if (!refreshed) {
      await _onSessionExpired?.call();
      throw ApiException(401, 'Oturum sona erdi. Tekrar giris yapin.');
    }
    response = await send();
    if (response.statusCode == 401) {
      await _onSessionExpired?.call();
      throw ApiException(401, 'Oturum sona erdi. Tekrar giris yapin.');
    }
    return response;
  }

  /// Refresh token ile yeni access alir; basariliysa [onSessionUpdated] cagirilir.
  Future<bool> tryRefreshAccessToken() async {
    final refresh = _refreshTokenProvider?.call();
    if (refresh == null || refresh.isEmpty) return false;
    try {
      final uri = Uri.parse('$backendBaseUrl/api/v1/aura/auth/refresh');
      final response = await _client
          .post(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'refreshToken': refresh}),
          )
          .timeout(_timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return false;
      }
      final session = AuthSession.fromSessionJson(
        jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
      );
      await _onSessionUpdated?.call(session);
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Deprecated demo JWT.
  Future<AuthSession> fetchAuthToken({String? username, int? userId}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/auth/token');
    final body = <String, dynamic>{
      'username': ?username,
      'userId': ?userId,
    };
    final response = await _client
        .post(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(body),
        )
        .timeout(_timeout);
    _ensureOk(response, 'JWT alinamadi');
    return AuthSession.fromDemoJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<void> register({
    required String email,
    required String password,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/auth/register');
    final response = await _client
        .post(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'email': email, 'password': password}),
        )
        .timeout(_timeout);
    _ensureOk(response, 'Kayit basarisiz');
  }

  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/auth/login');
    final response = await _client
        .post(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'email': email, 'password': password}),
        )
        .timeout(_timeout);
    _ensureOk(response, 'Giris basarisiz');
    return AuthSession.fromSessionJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<void> logout({required String refreshToken}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/auth/logout');
    final response = await _client
        .post(
          uri,
          headers: _jsonHeaders(auth: true),
          body: jsonEncode({'refreshToken': refreshToken}),
        )
        .timeout(_timeout);
    // 401 olsa bile lokal temizlenecek; sunucu hatasini yutma
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    if (response.statusCode == 204) return;
  }

  Future<List<WardrobeItem>> fetchWardrobe({
    int? userId,
    bool includeImages = true,
  }) async {
    final query = <String, String>{
      'includeImages': includeImages.toString(),
    };
    final uri = Uri.parse('$backendBaseUrl/api/v1/wardrobe/items')
        .replace(queryParameters: query);
    final response = await _authGet(uri);
    _ensureOk(response, 'Dolap listesi alinamadi');
    final list = jsonDecode(utf8.decode(response.bodyBytes)) as List<dynamic>;
    return list
        .whereType<Map<String, dynamic>>()
        .map(WardrobeItem.fromJson)
        .toList();
  }

  Future<SuggestionResponse> suggest({
    double? temperatureCelsius,
    double? humidityPercent,
    required String occasion,
    bool includeImages = true,
    int? userId,
    bool useAutoWeather = false,
    double? latitude,
    double? longitude,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/suggest');
    final body = <String, dynamic>{
      'occasion': occasion,
      'includeImages': includeImages,
      'useAutoWeather': useAutoWeather,
      'temperatureCelsius': ?temperatureCelsius,
      'humidityPercent': ?humidityPercent,
      'latitude': ?latitude,
      'longitude': ?longitude,
    };
    final response = await _authPost(uri, body: jsonEncode(body));
    _ensureOk(response, 'Oneri alinamadi');
    return SuggestionResponse.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<WeatherSnapshot> fetchWeather({double? lat, double? lon}) async {
    final query = <String, String>{
      if (lat != null) 'lat': '$lat',
      if (lon != null) 'lon': '$lon',
    };
    final uri = Uri.parse('$backendBaseUrl/api/v1/weather/current')
        .replace(queryParameters: query.isEmpty ? null : query);
    final response = await _client.get(uri).timeout(_timeout);
    _ensureOk(response, 'Hava durumu alinamadi');
    return WeatherSnapshot.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<AnalyzeResult> analyzeImage({
    required Uint8List bytes,
    required String fileName,
    String contentType = 'image/jpeg',
    bool forwardAuth = true,
  }) async {
    final uri = Uri.parse('$visionBaseUrl/api/v1/vision/analyze');
    final request = http.MultipartRequest('POST', uri)
      ..files.add(
        http.MultipartFile.fromBytes(
          'file',
          bytes,
          filename: fileName,
        ),
      );
    // forwardAuth=false: Vision backend sync cift kayit yazmasin (mobil tek POST yazar)
    if (forwardAuth) {
      final auth = _bearer;
      if (auth != null) {
        request.headers['Authorization'] = auth;
      }
    }
    final streamed =
        await _client.send(request).timeout(const Duration(minutes: 3));
    final response = await http.Response.fromStream(streamed);
    _ensureOk(response, 'Vision analizi basarisiz');
    return AnalyzeResult.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  /// Vision garment studio: dekupaj + 3:4 stüdyo framing → PNG baytlari.
  Future<Uint8List> normalizeGarment({
    required Uint8List bytes,
    required String fileName,
    String aspect = '3:4',
  }) async {
    final uri = Uri.parse('$visionBaseUrl/api/v1/vision/normalize-garment');
    final request = http.MultipartRequest('POST', uri)
      ..fields['aspect'] = aspect
      ..files.add(
        http.MultipartFile.fromBytes(
          'file',
          bytes,
          filename: fileName,
        ),
      );
    final streamed =
        await _client.send(request).timeout(const Duration(minutes: 2));
    final response = await http.Response.fromStream(streamed);
    _ensureOk(response, 'Garment normalize basarisiz');
    final json =
        jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    final b64 = json['image_base64'] as String? ?? json['imageBase64'] as String?;
    if (b64 == null || b64.isEmpty) {
      throw ApiException(500, 'Normalize cevabinda image_base64 yok');
    }
    return base64Decode(b64);
  }

  Future<({String uploadUrl, String objectUrl})> requestUploadUrl({
    required String purpose,
    required String contentType,
    String? filename,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/storage/upload-url');
    final body = <String, dynamic>{
      'purpose': purpose,
      'contentType': contentType,
      'filename': ?filename,
    };
    final response = await _authPost(uri, body: jsonEncode(body));
    _ensureOk(response, 'Upload URL alinamadi');
    final json =
        jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    return (
      uploadUrl: json['uploadUrl'] as String,
      objectUrl: json['objectUrl'] as String,
    );
  }

  /// Presigned PUT — dogrudan MinIO/R2'ye.
  Future<void> putObjectBytes({
    required String uploadUrl,
    required List<int> bytes,
    required String contentType,
  }) async {
    final response = await _client
        .put(
          Uri.parse(uploadUrl),
          headers: {'Content-Type': contentType},
          body: bytes,
        )
        .timeout(const Duration(minutes: 2));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        response.statusCode,
        'Object upload basarisiz (${response.statusCode})',
      );
    }
  }

  Future<String> uploadImageBytes({
    required String purpose,
    required List<int> bytes,
    String contentType = 'image/png',
    String? filename,
  }) async {
    final urls = await requestUploadUrl(
      purpose: purpose,
      contentType: contentType,
      filename: filename,
    );
    final uploadUrl = urls.uploadUrl;
    final isMemoryStub =
        uploadUrl.contains('presign=1') || uploadUrl.contains('/memory/');
    if (isMemoryStub) {
      // Test/offline memory provider — sunucu base64 migrate etsin.
      throw StateError('memory-storage-fallback');
    }
    await putObjectBytes(
      uploadUrl: uploadUrl,
      bytes: bytes,
      contentType: contentType,
    );
    return urls.objectUrl;
  }

  Future<WardrobeItem> createWardrobeItem({
    required String category,
    String? imageBase64,
    String? imageUrl,
    double? categoryConfidence,
    String? color,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/wardrobe/items');
    final body = <String, dynamic>{
      'category': category,
      'imageBase64': ?imageBase64,
      'imageUrl': ?imageUrl,
      'categoryConfidence': ?categoryConfidence,
      'color': ?color,
    };
    final response = await _authPost(uri, body: jsonEncode(body));
    _ensureOk(response, 'Dolaba yazma basarisiz');
    return WardrobeItem.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<List<UserPerfume>> fetchPerfumeCatalog() async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/user/perfumes/catalog');
    final response = await _authGet(uri);
    _ensureOk(response, 'Parfum katalogu alinamadi');
    final list = jsonDecode(utf8.decode(response.bodyBytes)) as List<dynamic>;
    return list
        .whereType<Map<String, dynamic>>()
        .map(UserPerfume.fromJson)
        .toList();
  }

  Future<List<UserPerfume>> fetchPerfumeShelf() async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/user/perfumes');
    final response = await _authGet(uri);
    _ensureOk(response, 'Parfum rafi alinamadi');
    final list = jsonDecode(utf8.decode(response.bodyBytes)) as List<dynamic>;
    return list
        .whereType<Map<String, dynamic>>()
        .map(UserPerfume.fromJson)
        .toList();
  }

  Future<UserPerfume> addPerfumeToShelf(String catalogId) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/user/perfumes');
    final response = await _authPost(
      uri,
      body: jsonEncode({'catalogId': catalogId}),
    );
    _ensureOk(response, 'Parfum rafa eklenemedi');
    return UserPerfume.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<void> removePerfumeFromShelf(int id) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/user/perfumes/$id');
    final response = await _authDelete(uri);
    _ensureOk(response, 'Parfum raftan cikarilamadi');
  }

  Future<OutfitFavorite> saveFavorite(SuggestionResponse suggestion) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/favorites');
    final perfume = suggestion.perfumeRecommendation;
    final body = <String, dynamic>{
      'vibe': suggestion.vibe,
      'summary': suggestion.summary,
      'occasion': suggestion.context.occasion,
      'temperatureCelsius': suggestion.context.temperatureCelsius,
      'seasonBand': suggestion.context.seasonBand,
      'matchScore': suggestion.matchScore,
      'colorHarmonyType': suggestion.colorHarmony?.type,
      'colorHarmonyScore': suggestion.colorHarmony?.score,
      'topItemId': suggestion.top?.item.id,
      'bottomItemId': suggestion.bottom?.item.id,
      'accessoryItemId': suggestion.accessory?.item.id,
      'perfumeCatalogId': perfume?.id,
      'perfumeLabel': perfume?.displayTitle,
    };
    final response = await _authPost(uri, body: jsonEncode(body));
    _ensureOk(response, 'Favori kaydedilemedi');
    return OutfitFavorite.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<List<OutfitFavorite>> fetchFavorites({int? userId}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/favorites');
    final response = await _authGet(uri);
    _ensureOk(response, 'Favoriler alinamadi');
    final list = jsonDecode(utf8.decode(response.bodyBytes)) as List<dynamic>;
    return list
        .whereType<Map<String, dynamic>>()
        .map(OutfitFavorite.fromJson)
        .toList();
  }

  Future<void> deleteFavorite(int id, {int? userId}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/favorites/$id');
    final response = await _authDelete(uri);
    _ensureOk(response, 'Favori silinemedi');
  }

  Future<ChatResponse> chat({
    required String message,
    List<ChatMessage> history = const [],
    int? userId,
    double? latitude,
    double? longitude,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/chat');
    final body = <String, dynamic>{
      'message': message,
      'history': history.map((m) => m.toApiJson()).toList(),
      'latitude': ?latitude,
      'longitude': ?longitude,
    };
    final response = await _authPost(
      uri,
      body: jsonEncode(body),
      timeout: const Duration(seconds: 120),
    );
    _ensureOk(response, 'Aura AI yanit uretemedi');
    return ChatResponse.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<VtonJob> requestVton({
    required int wardrobeItemId,
    String? personImageBase64,
    String? personImageUrl,
    int? userId,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/vton/request');
    final body = <String, dynamic>{
      'wardrobeItemId': wardrobeItemId,
      'personImageBase64': ?personImageBase64,
      'personImageUrl': ?personImageUrl,
      'userId': ?userId,
    };
    final response = await _authPost(uri, body: jsonEncode(body));
    _ensureOk(response, 'VTON istegi basarisiz');
    return VtonJob.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<VtonJob> fetchVtonStatus(int jobId, {int? userId}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/vton/status/$jobId');
    final response = await _authGet(uri);
    _ensureOk(response, 'VTON durumu alinamadi');
    return VtonJob.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  Future<List<VtonLookbookEntry>> fetchLookbook({int? userId}) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/vton/lookbook');
    final response = await _authGet(uri);
    _ensureOk(response, 'Lookbook yuklenemedi');
    final list = jsonDecode(utf8.decode(response.bodyBytes)) as List<dynamic>;
    return list
        .map((e) => VtonLookbookEntry.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<VtonLookbookEntry> saveToLookbook({
    required int jobId,
    int? userId,
  }) async {
    final uri = Uri.parse('$backendBaseUrl/api/v1/aura/vton/lookbook/$jobId');
    final response = await _withAuthRetry(
      () => _client.post(uri, headers: _authHeaders()).timeout(_timeout),
    );
    _ensureOk(response, "Lookbook'a eklenemedi");
    return VtonLookbookEntry.fromJson(
      jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>,
    );
  }

  String absoluteVtonResultUrl(String? path) {
    if (path == null || path.isEmpty) return '';
    if (path.startsWith('http')) return path;
    return '$backendBaseUrl$path';
  }

  Map<String, String> vtonImageHeaders([int? userId]) => _authHeaders();

  void _ensureOk(http.Response response, String fallback) {
    if (response.statusCode >= 200 && response.statusCode < 300) return;
    String detail = fallback;
    try {
      final json = jsonDecode(utf8.decode(response.bodyBytes));
      if (json is Map<String, dynamic>) {
        detail = (json['detail'] ?? json['title'] ?? fallback).toString();
      }
    } catch (_) {}
    throw ApiException(response.statusCode, detail);
  }
}

class ApiException implements Exception {
  ApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => 'ApiException($statusCode): $message';
}
