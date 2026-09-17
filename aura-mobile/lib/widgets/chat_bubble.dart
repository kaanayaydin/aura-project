import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';

import '../core/theme.dart';
import '../models/chat_message.dart';

/// Sohbet baloncuğu — asistan yanitlarinda zarif Markdown.
class ChatBubble extends StatelessWidget {
  const ChatBubble({super.key, required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.82,
        ),
        margin: const EdgeInsets.only(bottom: 12),
        padding: EdgeInsets.symmetric(
          horizontal: isUser ? 16 : 14,
          vertical: isUser ? 12 : 10,
        ),
        decoration: BoxDecoration(
          color: isUser
              ? AuraTheme.champagne.withValues(alpha: 0.92)
              : AuraTheme.carbonElevated,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(18),
            topRight: const Radius.circular(18),
            bottomLeft: Radius.circular(isUser ? 18 : 6),
            bottomRight: Radius.circular(isUser ? 6 : 18),
          ),
          border: isUser ? null : Border.all(color: AuraTheme.carbonSoft),
        ),
        child: isUser
            ? Text(
                message.content,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.carbon,
                      height: 1.45,
                    ),
              )
            : MarkdownBody(
                data: message.content,
                selectable: true,
                styleSheet: _assistantSheet(context),
                softLineBreak: true,
              ),
      ),
    );
  }

  MarkdownStyleSheet _assistantSheet(BuildContext context) {
    final base = Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: AuraTheme.mist,
          height: 1.5,
          fontSize: 14,
        );
    return MarkdownStyleSheet(
      p: base,
      strong: base?.copyWith(
        color: AuraTheme.champagne,
        fontWeight: FontWeight.w700,
      ),
      em: base?.copyWith(
        color: AuraTheme.mistMuted,
        fontStyle: FontStyle.italic,
      ),
      listBullet: base?.copyWith(color: AuraTheme.champagne),
      h1: base?.copyWith(
        fontSize: 18,
        fontWeight: FontWeight.w700,
        color: AuraTheme.champagne,
      ),
      h2: base?.copyWith(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        color: AuraTheme.champagne,
      ),
      h3: base?.copyWith(
        fontSize: 15,
        fontWeight: FontWeight.w600,
        color: AuraTheme.champagne,
      ),
      blockquote: base?.copyWith(color: AuraTheme.mistMuted),
      blockquoteDecoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            color: AuraTheme.champagne.withValues(alpha: 0.45),
            width: 3,
          ),
        ),
      ),
      blockquotePadding: const EdgeInsets.only(left: 12),
      code: base?.copyWith(
        color: AuraTheme.champagneDeep,
        backgroundColor: AuraTheme.carbonSoft,
        fontSize: 13,
      ),
      codeblockDecoration: BoxDecoration(
        color: AuraTheme.carbonSoft,
        borderRadius: BorderRadius.circular(10),
      ),
      horizontalRuleDecoration: BoxDecoration(
        border: Border(
          top: BorderSide(color: AuraTheme.carbonSoft.withValues(alpha: 0.9)),
        ),
      ),
      a: base?.copyWith(
        color: AuraTheme.champagne,
        decoration: TextDecoration.underline,
      ),
    );
  }
}
