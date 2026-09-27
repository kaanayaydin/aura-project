import 'package:aura_mobile/core/quiet_luxury/aura_colors.dart';
import 'package:aura_mobile/core/quiet_luxury/aura_shape.dart';
import 'package:aura_mobile/models/chat_message.dart';
import 'package:aura_mobile/widgets/chat_bubble.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('assistant bubble renders markdown emphasis', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ChatBubble(
            message: ChatMessage(
              role: 'assistant',
              content:
                  'Bugün sahne **26°C**.\n\n- **navy tişört**\n- gri pantolon\n\n_Aura notu: sessiz güç._',
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.textContaining('26°C'), findsWidgets);
    expect(find.textContaining('Aura notu'), findsWidgets);
    expect(find.byType(ChatBubble), findsOneWidget);

    final decoration = _bubbleDecoration(tester);
    expect(decoration.color, AuraColors.surfaceElevated);
    expect(decoration.boxShadow, AuraShadows.cardShadow);
    expect(decoration.border, isNull);
    expect(decoration.borderRadius, const BorderRadius.only(
      topLeft: Radius.circular(18),
      topRight: Radius.circular(18),
      bottomLeft: Radius.circular(6),
      bottomRight: Radius.circular(18),
    ));
    expect(_spanColor(tester, '26°C'), AuraColors.primaryAction);
    expect(_spanColor(tester, 'Aura notu'), AuraColors.textSecondary);
  });

  testWidgets('user bubble uses coffee fill and cream text', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ChatBubble(
            message: ChatMessage(role: 'user', content: 'Bugün ne giysem?'),
          ),
        ),
      ),
    );
    await tester.pump();

    final decoration = _bubbleDecoration(tester);
    expect(decoration.color, AuraColors.primaryAction);
    expect(decoration.boxShadow, AuraShadows.cardShadow);
    expect(decoration.border, isNull);
    expect(decoration.borderRadius, const BorderRadius.only(
      topLeft: Radius.circular(18),
      topRight: Radius.circular(18),
      bottomLeft: Radius.circular(18),
      bottomRight: Radius.circular(6),
    ));
    expect(
      tester.widget<Text>(find.text('Bugün ne giysem?')).style?.color,
      AuraColors.surface,
    );
  });
}

BoxDecoration _bubbleDecoration(WidgetTester tester) {
  final container = find.descendant(
    of: find.byType(Align),
    matching: find.byType(Container),
  ).first;
  return tester.widget<Container>(container).decoration! as BoxDecoration;
}

Color? _spanColor(WidgetTester tester, String fragment) {
  for (final selectable
      in tester.widgetList<SelectableText>(find.byType(SelectableText))) {
    final span = selectable.textSpan;
    if (span == null) continue;
    final color = _colorInSpan(span, fragment, inherited: span.style?.color);
    if (color != null) return color;
  }
  return null;
}

Color? _colorInSpan(
  InlineSpan span,
  String fragment, {
  Color? inherited,
}) {
  if (span is! TextSpan) return null;
  final color = span.style?.color ?? inherited;
  final text = span.text;
  if (text != null && text.contains(fragment)) return color;
  for (final child in span.children ?? const <InlineSpan>[]) {
    final found = _colorInSpan(child, fragment, inherited: color);
    if (found != null) return found;
  }
  return null;
}
