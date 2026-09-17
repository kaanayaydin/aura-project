import 'dart:typed_data';

import 'package:aura_mobile/core/theme.dart';
import 'package:aura_mobile/widgets/before_after_split_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';

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

  testWidgets('BeforeAfterSplitView renders slider and badges', (tester) async {
    final bytes = Uint8List.fromList(_tinyPng);
    await tester.pumpWidget(
      MaterialApp(
        theme: AuraTheme.dark,
        home: Scaffold(
          body: BeforeAfterSplitView(
            beforeBytes: bytes,
            afterPreviewBytes: bytes,
            height: 300,
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.byKey(const Key('vton-before-after')), findsOneWidget);
    expect(find.text('Önce'), findsOneWidget);
    expect(find.text('Sonra'), findsOneWidget);
    expect(find.textContaining('Sürgüyü'), findsOneWidget);

    await tester.drag(find.byKey(const Key('vton-before-after')), const Offset(40, 0));
    await tester.pump();
  });
}
