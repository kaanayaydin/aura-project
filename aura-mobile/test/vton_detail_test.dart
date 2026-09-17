import 'dart:convert';
import 'dart:typed_data';

import 'package:aura_mobile/core/theme.dart';
import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/models/vton_job.dart';
import 'package:aura_mobile/models/vton_lookbook_entry.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/screens/wardrobe_item_detail_screen.dart';
import 'package:aura_mobile/services/api_service.dart';
import 'package:aura_mobile/services/auth_secure_store.dart';
import 'package:aura_mobile/services/person_photo_service.dart';
import 'package:aura_mobile/widgets/vton_confirm_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';

const _tinyPng = [
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
  0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xDE, 0x00, 0x00, 0x00,
  0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
  0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD, 0x8D, 0xB4, 0x00, 0x00, 0x00,
  0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
];

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  test('PersonPhoto.fromBytes encodes base64', () {
    final bytes = Uint8List.fromList(_tinyPng);
    final photo = PersonPhoto.fromBytes(bytes);
    expect(photo.bytes, bytes);
    expect(base64Decode(photo.base64), bytes);
  });

  testWidgets('VtonConfirmCard shows Dönüştür', (tester) async {
    var confirmed = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: AuraTheme.dark,
        home: Scaffold(
          body: VtonConfirmCard(
            personBytes: Uint8List.fromList(_tinyPng),
            garmentBytes: Uint8List.fromList(_tinyPng),
            onConfirm: () => confirmed = true,
            onCancel: () {},
          ),
        ),
      ),
    );
    expect(find.byKey(const Key('vton-confirm-card')), findsOneWidget);
    expect(find.text('Dönüştür'), findsOneWidget);
    await tester.tap(find.byKey(const Key('vton-transform')));
    expect(confirmed, isTrue);
  });

  testWidgets('Sanal Dene → sheet → onay → Dönüştür completes VTON',
      (tester) async {
    final item = WardrobeItem.fromJson({
      'id': 3,
      'userId': 1,
      'category': 'blazer',
      'categoryConfidence': 0.88,
      'color': 'navy',
      'imageBytes': 4,
      'imageMimeType': 'image/png',
      'imageBase64': base64Encode(_tinyPng),
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authSecureStoreProvider.overrideWithValue(MemoryAuthSecureStore()),
          authSessionProvider.overrideWith(_StubAuthSession.new),
          apiServiceProvider.overrideWithValue(_FakeVtonApi()),
          personPhotoServiceProvider.overrideWithValue(_FakePersonPhotoService()),
          lookbookProvider.overrideWith(() => _StubLookbook()),
        ],
        child: MaterialApp(
          theme: AuraTheme.dark,
          home: WardrobeItemDetailScreen(item: item),
        ),
      ),
    );
    await tester.pump();

    await tester.tap(find.text('Sanal Dene'));
    await tester.pumpAndSettle();
    expect(find.text('Kamera ile Çek'), findsOneWidget);
    expect(find.text('Galeriden Seç'), findsOneWidget);

    await tester.tap(find.byKey(const Key('vton-source-gallery')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('vton-confirm-card')), findsOneWidget);
    expect(find.text('Dönüştür'), findsOneWidget);

    final transform = find.byKey(const Key('vton-transform'));
    await tester.ensureVisible(transform);
    await tester.pumpAndSettle();
    await tester.tap(transform);
    await tester.pump(); // start request
    await tester.pump(); // first poll microtask
    expect(find.textContaining('Sil'), findsWidgets);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    await tester.pump();

    expect(find.text('Sonuç'), findsOneWidget);
    expect(find.textContaining('hazir'), findsOneWidget);
    expect(find.byKey(const Key('lookbook-save')), findsOneWidget);
    expect(find.text("Lookbook'a Ekle"), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('lookbook-save')));
    await tester.tap(find.byKey(const Key('lookbook-save')));
    await tester.pumpAndSettle();
    expect(find.text('Kaydedildi'), findsOneWidget);
  });
}

class _FakePersonPhotoService extends PersonPhotoService {
  @override
  Future<PersonPhoto?> pick(ImageSource source) async {
    return PersonPhoto.fromBytes(Uint8List.fromList(_tinyPng));
  }
}

class _FakeVtonApi extends ApiService {
  int _polls = 0;
  String? lastPersonBase64;
  bool lookbookSaved = false;

  @override
  Future<AuthSession> fetchAuthToken({String? username, int? userId}) async {
    return AuthSession(
      accessToken: 'test-jwt',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      userId: userId ?? 1,
      username: username ?? 'demo',
      isDemo: true,
    );
  }

  @override
  Future<VtonJob> requestVton({
    required int wardrobeItemId,
    String? personImageBase64,
    String? personImageUrl,
    int? userId,
  }) async {
    lastPersonBase64 = personImageBase64;
    expect(
      (personImageBase64 != null && personImageBase64.isNotEmpty) ||
          (personImageUrl != null && personImageUrl.isNotEmpty),
      isTrue,
    );
    return VtonJob(
      jobId: 11,
      userId: userId ?? 1,
      wardrobeItemId: wardrobeItemId,
      status: 'QUEUED',
      workerJobId: 'w-11',
    );
  }

  @override
  Future<VtonJob> fetchVtonStatus(int jobId, {int? userId}) async {
    _polls++;
    if (_polls == 1) {
      return VtonJob(
        jobId: jobId,
        userId: userId ?? 1,
        wardrobeItemId: 3,
        status: 'PROCESSING',
        workerJobId: 'w-11',
      );
    }
    return VtonJob(
      jobId: jobId,
      userId: userId ?? 1,
      wardrobeItemId: 3,
      status: 'COMPLETED',
      workerJobId: 'w-11',
      resultImageUri: 'mock://vton/result/$jobId',
      // Test ortami NetworkImage'i engeller; onizleme base64 yeter.
      resultImageUrl: null,
      resultImageBase64: base64Encode(_tinyPng),
      lookbookSaved: lookbookSaved,
    );
  }

  @override
  Future<List<VtonLookbookEntry>> fetchLookbook({int? userId}) async {
    if (!lookbookSaved) return const [];
    return [
      VtonLookbookEntry(
        jobId: 11,
        userId: userId ?? 1,
        wardrobeItemId: 3,
        category: 'blazer',
        color: 'navy',
        resultImageUrl: '/api/v1/aura/vton/results/11/image',
        resultImageBase64: base64Encode(_tinyPng),
        lookbookSaved: true,
      ),
    ];
  }

  @override
  Future<VtonLookbookEntry> saveToLookbook({
    required int jobId,
    int? userId,
  }) async {
    lookbookSaved = true;
    return VtonLookbookEntry(
      jobId: jobId,
      userId: userId ?? 1,
      wardrobeItemId: 3,
      category: 'blazer',
      color: 'navy',
      resultImageUrl: '/api/v1/aura/vton/results/$jobId/image',
      resultImageBase64: base64Encode(_tinyPng),
      lookbookSaved: true,
    );
  }
}

class _StubLookbook extends LookbookNotifier {
  @override
  Future<List<VtonLookbookEntry>> build() async => const [];
}

class _StubAuthSession extends AuthSessionNotifier {
  @override
  AuthSession? build() => const AuthSession(
        accessToken: 'test-jwt',
        refreshToken: 'test-refresh',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 1,
        email: 'demo@aura.app',
        username: 'demo',
        isDemo: true,
      );

  @override
  Future<AuthSession> ensure({int? userId, String? username}) async => state!;
}
