import 'package:aura_mobile/core/theme.dart';
import 'package:aura_mobile/widgets/vton_daily_limit_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('VTON günlük limit diyaloğu Şampanya/Karbon temalı', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AuraTheme.dark,
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: TextButton(
                key: const Key('open-limit'),
                onPressed: () => showVtonDailyLimitDialog(context),
                child: const Text('open'),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('open-limit')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('vton-daily-limit-dialog')), findsOneWidget);
    expect(find.text('Günlük deneme sınırına ulaşıldı'), findsOneWidget);

    await tester.tap(find.byKey(const Key('vton-daily-limit-ok')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('vton-daily-limit-dialog')), findsNothing);
  });
}
