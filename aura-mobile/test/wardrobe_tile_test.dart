import 'package:aura_mobile/core/quiet_luxury/aura_colors.dart';
import 'package:aura_mobile/core/quiet_luxury/aura_shape.dart';
import 'package:aura_mobile/models/wardrobe_item.dart';
import 'package:aura_mobile/widgets/wardrobe_tile.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('wardrobe tile shows Turkish category and hides confidence', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: WardrobeTile(
            item: WardrobeItem(
              id: 1,
              userId: 1,
              category: 't-shirt',
              categoryConfidence: 0.8,
              imageBytes: 0,
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('tişört'), findsOneWidget);
    expect(find.textContaining('guven'), findsNothing);
    expect(find.textContaining('80%'), findsNothing);

    final decoration = tester
        .widget<Container>(find.byType(Container).first)
        .decoration! as BoxDecoration;
    expect(decoration.color, AuraColors.surfaceElevated);
    expect(decoration.boxShadow, AuraShadows.cardShadow);
    expect(
      decoration.borderRadius,
      BorderRadius.circular(AuraRadii.cardRadius),
    );
  });
}
