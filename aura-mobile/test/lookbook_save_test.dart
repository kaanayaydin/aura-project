import 'package:aura_mobile/core/theme.dart';
import 'package:aura_mobile/widgets/lookbook_save_action.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  testWidgets('LookbookSaveAction toggles label by saved state', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AuraTheme.dark,
        home: Scaffold(
          body: LookbookSaveAction(
            saved: false,
            busy: false,
            onPressed: () {},
          ),
        ),
      ),
    );
    expect(find.text("Lookbook'a Ekle"), findsOneWidget);
    expect(find.byKey(const Key('lookbook-save')), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        theme: AuraTheme.dark,
        home: const Scaffold(
          body: LookbookSaveAction(
            saved: true,
            busy: false,
            onPressed: null,
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Kaydedildi'), findsOneWidget);
  });
}
