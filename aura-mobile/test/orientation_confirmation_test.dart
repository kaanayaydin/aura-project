import 'dart:convert';
import 'dart:typed_data';

import 'package:aura_mobile/core/theme.dart';
import 'package:aura_mobile/models/normalize_garment_result.dart';
import 'package:aura_mobile/models/orientation_choice.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:aura_mobile/models/wardrobe_upload_outcome.dart';
import 'package:aura_mobile/providers/providers.dart';
import 'package:aura_mobile/screens/orientation_confirmation_screen.dart';
import 'package:aura_mobile/screens/wardrobe_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  final medium = _result(ensemble: 'medium', suggested: 'right');
  final high = _result(ensemble: 'high', suggested: 'top', requires: false);
  final low = _result(ensemble: 'low', suggested: 'top');

  testWidgets('medium confidence response shows OrientationConfirmationScreen',
      (tester) async {
    expect(shouldShowOrientationConfirmation(medium), isTrue);
    await tester.pumpWidget(_app(OrientationConfirmationScreen(result: medium)));
    await tester.pump();
    expect(find.byKey(const Key('orientation-confirmation-screen')), findsOneWidget);
    expect(find.text('Kıyafeti böyle mi çevirelim?'), findsOneWidget);
    expect(find.text('Evet, doğru'), findsOneWidget);
    expect(find.text('Onayla'), findsOneWidget);
    expect(find.text('Emin değilim, orijinal görseli kullan'), findsNothing);
  });

  testWidgets('high confidence response does not show confirmation UI',
      (tester) async {
    expect(shouldShowOrientationConfirmation(high), isFalse);
    await tester.pumpWidget(
      _app(
        shouldShowOrientationConfirmation(high)
            ? OrientationConfirmationScreen(result: high)
            : const Text('no-confirm'),
      ),
    );
    await tester.pump();
    expect(find.byKey(const Key('orientation-confirmation-screen')), findsNothing);
    expect(find.text('no-confirm'), findsOneWidget);
    expect(find.text('Kıyafeti böyle mi çevirelim?'), findsNothing);
  });

  testWidgets('low confidence shows escape option and copy', (tester) async {
    expect(shouldShowOrientationConfirmation(low), isTrue);
    await tester.pumpWidget(_app(OrientationConfirmationScreen(result: low)));
    await tester.pump();
    expect(
      find.text('Yönünü tam çözemedik, yardımcı olur musun?'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('orientation-use-original')), findsOneWidget);
  });

  testWidgets('Evet doğru → confirmed_rotation_deg = suggested CCW',
      (tester) async {
    final decision = await _openTapConfirm(
      tester,
      medium,
      () async {
        await tester.tap(find.byKey(const Key('orientation-confirm-yes')));
        await tester.pump();
      },
    );
    expect(decision, isNotNull);
    expect(decision!.confirmedRotationDeg, 90);
    expect(decision.acceptedSuggestion, isTrue);
    expect(decision.manualAdjust, isFalse);
    expect(decision.useOriginal, isFalse);
  });

  testWidgets('manuel 90° sağa → confirmed_rotation_deg 270 from top',
      (tester) async {
    final decision = await _openTapConfirm(
      tester,
      low,
      () async {
        await tester.tap(find.byKey(const Key('orientation-rotate-cw')));
        await tester.pump();
      },
    );
    expect(decision, isNotNull);
    expect(decision!.confirmedRotationDeg, 270);
    expect(decision.acceptedSuggestion, isFalse);
    expect(decision.manualAdjust, isTrue);
    expect(decision.useOriginal, isFalse);
  });

  testWidgets('orijinal görseli kullan → confirmed_rotation_deg 0',
      (tester) async {
    final decision = await _openTapConfirm(
      tester,
      low,
      () async {
        await tester.tap(find.byKey(const Key('orientation-use-original')));
        await tester.pump();
      },
    );
    expect(decision, isNotNull);
    expect(decision!.confirmedRotationDeg, 0);
    expect(decision.useOriginal, isTrue);
    expect(decision.acceptedSuggestion, isFalse);
    expect(decision.manualAdjust, isFalse);
  });

  testWidgets('saving phase shows optimistic thumbnail placeholder',
      (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          wardrobeProvider.overrideWith(() => _EmptyWardrobe()),
          wardrobeUploadPhaseProvider.overrideWith(() => _SavingPhase()),
        ],
        child: MaterialApp(
          theme: AuraTheme.dark,
          home: const WardrobeScreen(),
        ),
      ),
    );
    await tester.pump();
    expect(find.byKey(const Key('pending-wardrobe-thumb')), findsOneWidget);
    expect(find.text('kaydediliyor…'), findsOneWidget);
  });
}

Widget _app(Widget home) {
  return MaterialApp(
    theme: AuraTheme.dark,
    home: home,
  );
}

Future<OrientationConfirmDecision?> _openTapConfirm(
  WidgetTester tester,
  NormalizeGarmentResult result,
  Future<void> Function() beforeSubmit,
) async {
  OrientationConfirmDecision? captured;
  await tester.pumpWidget(
    MaterialApp(
      theme: AuraTheme.dark,
      home: Builder(
        builder: (context) {
          return Scaffold(
            body: TextButton(
              key: const Key('open-confirm'),
              onPressed: () async {
                captured = await Navigator.of(context).push(
                  MaterialPageRoute<OrientationConfirmDecision>(
                    builder: (_) => OrientationConfirmationScreen(result: result),
                  ),
                );
              },
              child: const Text('open'),
            ),
          );
        },
      ),
    ),
  );
  await tester.tap(find.byKey(const Key('open-confirm')));
  await tester.pumpAndSettle();
  await beforeSubmit();
  await tester.tap(find.byKey(const Key('orientation-submit')));
  await tester.pumpAndSettle();
  return captured;
}

NormalizeGarmentResult _result({
  required String ensemble,
  required String suggested,
  bool? requires,
}) {
  return NormalizeGarmentResult(
    imageBytes: _tinyPng,
    jobId: 'job-test',
    ensembleConfidence: ensemble,
    rotationSuggested: suggested,
    requiresConfirmation: requires ?? (ensemble != 'high'),
    rotationDegApplied: 0,
    rotationMethod: ensemble == 'high' ? 'none' : 'skipped_pending_confirmation',
  );
}

class _EmptyWardrobe extends WardrobeNotifier {
  @override
  Future<List<WardrobeItem>> build() async => const [];
}

class _SavingPhase extends WardrobeUploadPhaseNotifier {
  @override
  WardrobeUploadPhase build() {
    return WardrobeUploadSaving(
      previewBytes: _tinyPng,
      category: 't-shirt',
    );
  }
}

final Uint8List _tinyPng = Uint8List.fromList(base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhAGAhKMMowAAAABJRU5ErkJggg==',
));
