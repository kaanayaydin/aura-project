import 'package:aura_mobile/models/analyze_result.dart';
import 'package:aura_mobile/models/auth_token.dart';
import 'package:aura_mobile/models/chat_message.dart';
import 'package:aura_mobile/models/normalize_garment_result.dart';
import 'package:aura_mobile/models/orientation_choice.dart';
import 'package:aura_mobile/models/outfit_favorite.dart';
import 'package:aura_mobile/models/suggestion.dart';
import 'package:aura_mobile/models/vton_job.dart';
import 'package:aura_mobile/models/vton_lookbook_entry.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('AnalyzeResult parses cutout_image_base64 for wardrobe save', () {
    final result = AnalyzeResult.fromJson({
      'message': 'ok',
      'stages_completed': ['metadata', 'detection', 'segmentation', 'classification'],
      'detected_items': [
        {
          'label': 'person',
          'category': 'jacket',
          'category_confidence': 0.88,
          'cutout_image_base64': 'iVBOR',
          'segmentation': {'source': 'sam', 'mask_area_px': 10, 'box_coverage': 0.5},
        },
        {
          'label': 'tie',
          'category': 'tie',
          'category_confidence': 0.4,
        },
      ],
    });
    expect(result.withCutouts, hasLength(1));
    expect(result.withCutouts.first.category, 'jacket');
    expect(result.withCutouts.first.cutoutImageBase64, 'iVBOR');
    expect(result.withCutouts.first.segmentationSource, 'sam');
  });

  test('WardrobeItem parses backend JSON', () {
    final item = WardrobeItem.fromJson({
      'id': 3,
      'userId': 1,
      'category': 't-shirt',
      'categoryConfidence': 0.91,
      'color': null,
      'imageBytes': 208,
      'imageMimeType': 'image/png',
      'imageUrl': 'http://127.0.0.1:9000/aura-wardrobe/items/x.png',
      'imageBase64': 'aGVsbG8=',
      'imageDataUri': 'data:image/png;base64,aGVsbG8=',
    });
    expect(item.category, 't-shirt');
    expect(item.decodedBytes, isNotNull);
    expect(item.confidenceLabel, '91%');
  });

  test('AuthToken parses JWT response', () {
    final token = AuthToken.fromJson({
      'accessToken': 'abc.def.ghi',
      'tokenType': 'Bearer',
      'expiresInMinutes': 60,
      'userId': 1,
      'username': 'demo',
    });
    expect(token.authorizationHeader, 'Bearer abc.def.ghi');
    expect(token.userId, 1);
    expect(token.isDemo, isTrue);
  });

  test('AuthSession parses login response with refresh', () {
    final session = AuthSession.fromSessionJson({
      'accessToken': 'a.b.c',
      'refreshToken': 'refresh-raw',
      'tokenType': 'Bearer',
      'expiresIn': 900,
      'userId': 7,
      'email': 'kaan@aura.app',
    });
    expect(session.hasRefreshToken, isTrue);
    expect(session.expiresInSeconds, 900);
    expect(session.email, 'kaan@aura.app');
    expect(session.isDemo, isFalse);
  });

  test('VtonJob parses status payload', () {
    final job = VtonJob.fromJson({
      'jobId': 9,
      'userId': 1,
      'wardrobeItemId': 3,
      'status': 'COMPLETED',
      'workerJobId': '42',
      'resultImageUri': 'http://127.0.0.1:8001/outputs/9.png',
      'resultImageUrl': '/api/v1/aura/vton/results/9/image',
      'resultImageBase64': 'aGVsbG8=',
      'errorMessage': null,
      'lookbookSaved': true,
    });
    expect(job.isCompleted, isTrue);
    expect(job.isTerminal, isTrue);
    expect(job.resultImageBase64, 'aGVsbG8=');
    expect(job.resultImageUrl, contains('/results/9/image'));
    expect(job.lookbookSaved, isTrue);
  });

  test('VtonLookbookEntry parses lookbook JSON', () {
    final entry = VtonLookbookEntry.fromJson({
      'jobId': 9,
      'userId': 1,
      'wardrobeItemId': 3,
      'category': 'dress',
      'color': 'black',
      'resultImageUrl': '/api/v1/aura/vton/results/9/image',
      'resultImageBase64': 'aGVsbG8=',
      'createdAt': '2026-09-13T00:00:00Z',
      'lookbookSaved': true,
    });
    expect(entry.category, 'dress');
    expect(entry.lookbookSaved, isTrue);
    expect(entry.createdAt, isNotNull);
  });

  test('SuggestionResponse maps slots', () {
    final suggestion = SuggestionResponse.fromJson({
      'userId': 1,
      'vibe': 'Dengeli & Resmi',
      'summary': 'test',
      'context': {
        'temperatureCelsius': 18.0,
        'humidityPercent': 50.0,
        'occasion': 'meeting',
        'seasonBand': 'mild',
      },
      'top': {
        'role': 'top',
        'score': 0.8,
        'reason': 'shirt',
        'item': {
          'id': 9,
          'userId': 1,
          'category': 'shirt',
          'imageBytes': 12,
          'imageMimeType': 'image/png',
          'imageBase64': null,
          'imageDataUri': null,
        },
      },
      'bottom': null,
      'accessory': null,
      'perfumeRecommendation': {
        'id': 'adp-colonia',
        'brand': 'Acqua di Parma',
        'name': 'Colonia',
        'concentration': 'EDC',
        'chords': ['citrus', 'fresh'],
        'topNotes': ['bergamot', 'lemon'],
        'heartNotes': ['lavender'],
        'baseNotes': ['vetiver'],
        'diffusion': 'light',
        'blurb': 'Ferah klasik.',
        'score': 0.88,
        'reason': 'mild mevsimine birebir',
        'thermodynamicNote': 'Ilik havada dengeli.',
      },
      'colorHarmony': {
        'type': 'analogous',
        'score': 0.88,
        'explanation': 'Notr ton her renkle uyumlu.',
      },
      'matchScore': 0.76,
      'notes': ['Dolapta uygun alt giyim bulunamadi.'],
    });
    expect(suggestion.vibe, contains('Resmi'));
    expect(suggestion.top?.item.category, 'shirt');
    expect(suggestion.bottom, isNull);
    expect(suggestion.filledSlots, 1);
    expect(suggestion.perfumeRecommendation?.brand, 'Acqua di Parma');
    expect(suggestion.perfumeRecommendation?.topNotes, contains('bergamot'));
    expect(suggestion.perfumeRecommendation?.displayTitle, contains('Colonia'));
    expect(suggestion.colorHarmony?.typeLabel, 'Uyumlu ton');
  });

  test('OutfitFavorite parses favorites JSON', () {
    final favorite = OutfitFavorite.fromJson({
      'id': 12,
      'userId': 1,
      'vibe': 'Soft Power',
      'summary': 'Ilik gunde dengeli kombin',
      'occasion': 'casual',
      'temperatureCelsius': 21.5,
      'seasonBand': 'mild',
      'matchScore': 0.82,
      'colorHarmonyType': 'neutral',
      'colorHarmonyScore': 0.9,
      'topItemId': 3,
      'bottomItemId': 7,
      'accessoryItemId': null,
      'perfumeCatalogId': 'adp-colonia',
      'perfumeLabel': 'Acqua di Parma — Colonia',
      'createdAt': '2026-09-11T18:00:00Z',
    });
    expect(favorite.id, 12);
    expect(favorite.occasionLabel, 'Gunluk');
    expect(favorite.colorHarmonyLabel, 'Notr');
    expect(favorite.pieceCount, 2);
    expect(favorite.perfumeLabel, contains('Colonia'));
    expect(favorite.createdAt, isNotNull);
  });

  test('ChatResponse parses backend JSON', () {
    final chat = ChatResponse.fromJson({
      'reply': 'Bugun soft katmanli bir kombin dene.',
      'model': 'llama3.2',
      'source': 'ollama',
      'wardrobeCount': 5,
      'perfumeCount': 2,
      'weatherSummary': '21°C, Partly cloudy, Istanbul (open-meteo)',
    });
    expect(chat.reply, contains('kombin'));
    expect(chat.isFallback, isFalse);
    expect(chat.wardrobeCount, 5);
  });

  test('NormalizeGarmentResult parses snake_case orientation fields', () {
    final result = NormalizeGarmentResult.fromJson({
      'image_base64': _tinyPngB64,
      'job_id': 'job-1',
      'ensemble_confidence': 'medium',
      'rotation_suggested': 'right',
      'requires_confirmation': true,
      'rotation_deg_applied': 0,
      'rotation_method': 'skipped_pending_confirmation',
      'width': 768,
      'height': 1024,
    });
    expect(result.jobId, 'job-1');
    expect(result.ensembleConfidence, 'medium');
    expect(result.rotationSuggested, 'right');
    expect(result.requiresConfirmation, isTrue);
    expect(result.rotationDegApplied, 0);
    expect(result.rotationMethod, 'skipped_pending_confirmation');
    expect(result.needsConfirmation, isTrue);
    expect(result.imageBytes, isNotEmpty);
  });

  test('NormalizeGarmentResult camelCase and missing requiresConfirmation → false', () {
    final result = NormalizeGarmentResult.fromJson({
      'imageBase64': _tinyPngB64,
      'jobId': 'legacy',
      'ensembleConfidence': 'high',
      'rotationSuggested': 'top',
      'rotationDegApplied': 0,
      'rotationMethod': 'none',
    });
    expect(result.requiresConfirmation, isFalse);
    expect(result.isHigh, isTrue);
    expect(result.needsConfirmation, isFalse);
    expect(result.jobId, 'legacy');
  });

  test('NormalizeGarmentResult legacy JSON without ensemble fields does not break', () {
    final result = NormalizeGarmentResult.fromJson({
      'image_base64': _tinyPngB64,
      'message': 'ok',
      'width': 10,
      'height': 10,
    });
    expect(result.requiresConfirmation, isFalse);
    expect(result.ensembleConfidence, '');
    expect(result.rotationSuggested, 'top');
    expect(result.rotationDegApplied, 0);
    expect(result.rotationMethod, 'none');
    expect(result.needsConfirmation, isFalse);
  });

  test('high never needs confirmation even if flag true', () {
    final result = NormalizeGarmentResult.fromJson({
      'image_base64': _tinyPngB64,
      'ensemble_confidence': 'high',
      'requires_confirmation': true,
      'rotation_suggested': 'bottom',
    });
    expect(result.needsConfirmation, isFalse);
  });

  test('OrientationChoice confirmed_rotation_deg: evet / manuel / orijinal', () {
    expect(OrientationChoice.suggestedCcwDeg('right'), 90);
    expect(OrientationChoice.suggestedCcwDeg('left'), 270);
    expect(OrientationChoice.suggestedCcwDeg('bottom'), 180);
    expect(OrientationChoice.suggestedCcwDeg('top'), 0);

    expect(OrientationChoice.suggestedCwQuarterTurns('right'), 3);
    expect(OrientationChoice.suggestedCwQuarterTurns('left'), 1);
    expect(OrientationChoice.suggestedCwQuarterTurns('bottom'), 2);
    expect(OrientationChoice.suggestedCwQuarterTurns('top'), 0);

    expect(OrientationChoice.cwTurnsToCcwDeg(0), 0);
    expect(OrientationChoice.cwTurnsToCcwDeg(1), 270);
    expect(OrientationChoice.cwTurnsToCcwDeg(2), 180);
    expect(OrientationChoice.cwTurnsToCcwDeg(3), 90);

    final yesRight = OrientationChoice.confirmedRotationDeg(
      useOriginal: false,
      previewCwTurns: OrientationChoice.suggestedCwQuarterTurns('right'),
    );
    expect(yesRight, 90);

    final manualCwFromTop = OrientationChoice.confirmedRotationDeg(
      useOriginal: false,
      previewCwTurns: OrientationChoice.addCwTurns(
        OrientationChoice.suggestedCwQuarterTurns('top'),
        1,
      ),
    );
    expect(manualCwFromTop, 270);

    final original = OrientationChoice.confirmedRotationDeg(
      useOriginal: true,
      previewCwTurns: 3,
    );
    expect(original, 0);
  });
}

const _tinyPngB64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhAGAhKMMowAAAABJRU5ErkJggg==';
