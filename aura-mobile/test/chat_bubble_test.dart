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
    await tester.pumpAndSettle();
    expect(find.textContaining('26°C'), findsWidgets);
    expect(find.textContaining('Aura notu'), findsWidgets);
    expect(find.byType(ChatBubble), findsOneWidget);
  });
}
