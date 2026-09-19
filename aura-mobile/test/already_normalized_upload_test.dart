import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/models/normalize_garment_result.dart';
import 'package:aura_mobile/models/orientation_choice.dart';
import 'package:aura_mobile/models/wardrobe_upload_outcome.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/services/api_service.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:aura_mobile/services/image_rotate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final png = Uint8List.fromList(base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhAGAhKMMowAAAABJRU5ErkJggg==',
  ));

  // testWidgets degil: ApiService Future.timeout(60s) sahte zamanlayici birakir
  // ve widget baglaminda test asili kalir.
  test(
    'confirmation upload POST body has alreadyNormalized true for all three choices',
    () async {
      final capturedBodies = <Map<String, dynamic>>[];
      final client = MockClient((request) async {
        if (request.method == 'POST' &&
            request.url.path.endsWith('/api/v1/wardrobe/items')) {
          capturedBodies.add(
            jsonDecode(request.body) as Map<String, dynamic>,
          );
          return http.Response(
            jsonEncode({
              'id': capturedBodies.length,
              'userId': 1,
              'category': 't-shirt',
              'imageBytes': png.length,
            }),
            201,
          );
        }
        if (request.method == 'GET' &&
            request.url.path.endsWith('/api/v1/wardrobe/items')) {
          return http.Response('[]', 200);
        }
        return http.Response(
          'unexpected ${request.method} ${request.url}',
          404,
        );
      });

      final container = ProviderContainer(
        overrides: [
          authSecureStoreProvider.overrideWithValue(MemoryAuthSecureStore()),
          authSessionProvider.overrideWith(_SeededAuth.new),
          apiServiceProvider.overrideWithValue(
            ApiService(
              client: client,
              backendBaseUrl: 'http://backend.test',
              visionBaseUrl: 'http://vision.test',
              accessTokenProvider: () => 'test-access',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(wardrobeProvider.future);

      final pending = PendingOrientationConfirmation(
        normalize: NormalizeGarmentResult(
          imageBytes: png,
          jobId: 'job-confirm',
          ensembleConfidence: 'medium',
          rotationSuggested: 'right',
          requiresConfirmation: true,
        ),
        category: 't-shirt',
        categoryConfidence: 0.7,
      );

      // OrientationConfirmationScreen uc dugmesiyle ayni kararlar
      // (evet / manuel / orijinal). previewCwTurns=0: PNG codec yok;
      // bayrak donuse bagli degil — ucu de zaten kesinlesmis gorsel.
      final decisions = <OrientationConfirmDecision>[
        const OrientationConfirmDecision(
          confirmedRotationDeg: 90,
          previewCwTurns: 0,
          acceptedSuggestion: true,
          useOriginal: false,
          manualAdjust: false,
        ),
        const OrientationConfirmDecision(
          confirmedRotationDeg: 270,
          previewCwTurns: 0,
          acceptedSuggestion: false,
          useOriginal: false,
          manualAdjust: true,
        ),
        const OrientationConfirmDecision(
          confirmedRotationDeg: 0,
          previewCwTurns: 0,
          acceptedSuggestion: false,
          useOriginal: true,
          manualAdjust: false,
        ),
      ];

      for (final decision in decisions) {
        await container.read(wardrobeProvider.notifier).completeConfirmedUpload(
              pending: pending,
              decision: decision,
            );
      }

      expect(capturedBodies, hasLength(3));
      for (final body in capturedBodies) {
        expect(body['alreadyNormalized'], isTrue);
        expect(body['category'], 't-shirt');
        expect(body['imageBase64'], isNotEmpty);
      }
    },
    timeout: const Timeout(Duration(seconds: 15)),
  );

  test(
    'createWardrobeItem default alreadyNormalized is false in JSON',
    () async {
      Map<String, dynamic>? body;
      final client = MockClient((request) async {
        body = jsonDecode(request.body) as Map<String, dynamic>;
        return http.Response(
          jsonEncode({
            'id': 1,
            'userId': 1,
            'category': 'shirt',
            'imageBytes': 1,
          }),
          201,
        );
      });
      final api = ApiService(
        client: client,
        backendBaseUrl: 'http://backend.test',
        visionBaseUrl: 'http://vision.test',
        accessTokenProvider: () => 'test-access',
      );
      await api.createWardrobeItem(
        category: 'shirt',
        imageBase64: base64Encode(png),
      );
      expect(body, isNotNull);
      expect(body!['alreadyNormalized'], isFalse);
    },
    timeout: const Timeout(Duration(seconds: 10)),
  );

  test(
    'rotatePngClockwise swaps 6x8 portrait to 8x6 landscape',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      final src = await _solidPng(6, 8);
      final rotated = await rotatePngClockwise(src, 1);
      final size = await _pngSize(rotated);
      expect(size.$1, 8);
      expect(size.$2, 6);
    },
    timeout: const Timeout(Duration(seconds: 10)),
  );

  test(
    'confirmation upload with previewCwTurns=1 POSTs swapped PNG bytes',
    () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      final portrait = await _solidPng(6, 8);
      Map<String, dynamic>? body;
      final client = MockClient((request) async {
        if (request.method == 'POST' &&
            request.url.path.endsWith('/api/v1/wardrobe/items')) {
          body = jsonDecode(request.body) as Map<String, dynamic>;
          return http.Response(
            jsonEncode({
              'id': 1,
              'userId': 1,
              'category': 't-shirt',
              'imageBytes': 1,
            }),
            201,
          );
        }
        if (request.method == 'GET' &&
            request.url.path.endsWith('/api/v1/wardrobe/items')) {
          return http.Response('[]', 200);
        }
        return http.Response('unexpected', 404);
      });
      final container = ProviderContainer(
        overrides: [
          authSecureStoreProvider.overrideWithValue(MemoryAuthSecureStore()),
          authSessionProvider.overrideWith(_SeededAuth.new),
          apiServiceProvider.overrideWithValue(
            ApiService(
              client: client,
              backendBaseUrl: 'http://backend.test',
              visionBaseUrl: 'http://vision.test',
              accessTokenProvider: () => 'test-access',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      await container.read(wardrobeProvider.future);
      await container.read(wardrobeProvider.notifier).completeConfirmedUpload(
            pending: PendingOrientationConfirmation(
              normalize: NormalizeGarmentResult(
                imageBytes: portrait,
                jobId: 'job-rotate',
                ensembleConfidence: 'medium',
                rotationSuggested: 'right',
                requiresConfirmation: true,
              ),
              category: 't-shirt',
            ),
            decision: const OrientationConfirmDecision(
              confirmedRotationDeg: 270,
              previewCwTurns: 1,
              acceptedSuggestion: false,
              useOriginal: false,
              manualAdjust: true,
            ),
          );
      expect(body, isNotNull);
      expect(body!['alreadyNormalized'], isTrue);
      final stored = base64Decode(body!['imageBase64'] as String);
      final size = await _pngSize(Uint8List.fromList(stored));
      expect(size.$1, 8);
      expect(size.$2, 6);
    },
    timeout: const Timeout(Duration(seconds: 15)),
  );
}

Future<Uint8List> _solidPng(int width, int height) async {
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  canvas.drawRect(
    ui.Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
    ui.Paint()..color = const ui.Color(0xFFFF0000),
  );
  final image = await recorder.endRecording().toImage(width, height);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  if (data == null) {
    throw StateError('PNG encode failed');
  }
  return data.buffer.asUint8List();
}

Future<(int, int)> _pngSize(Uint8List bytes) async {
  final codec = await ui.instantiateImageCodec(bytes);
  final frame = await codec.getNextFrame();
  final size = (frame.image.width, frame.image.height);
  frame.image.dispose();
  return size;
}

class _SeededAuth extends AuthSessionNotifier {
  @override
  AuthSession? build() => const AuthSession(
        accessToken: 'test-access',
        refreshToken: 'test-refresh',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 1,
        email: 'test@aura.app',
      );
}
