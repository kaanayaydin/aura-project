import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/chat_message.dart';

/// Sohbet baloncuğu — asistan yanıtlarında sade Markdown.
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
        margin: const EdgeInsets.only(bottom: 16),
        padding: EdgeInsets.symmetric(
          horizontal: isUser ? 16 : 14,
          vertical: isUser ? 12 : 10,
        ),
        decoration: BoxDecoration(
          color: isUser ? AuraColors.primaryAction : AuraColors.surfaceElevated,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(18),
            topRight: const Radius.circular(18),
            bottomLeft: Radius.circular(isUser ? 18 : 6),
            bottomRight: Radius.circular(isUser ? 6 : 18),
          ),
          boxShadow: AuraShadows.cardShadow,
        ),
        child: isUser
            ? Text(
                message.content,
                style: AuraTypography.body.copyWith(
                  color: AuraColors.surface,
                  height: 1.45,
                ),
              )
            : MarkdownBody(
                data: message.content,
                selectable: true,
                styleSheet: _assistantSheet(),
                softLineBreak: true,
              ),
      ),
    );
  }

  MarkdownStyleSheet _assistantSheet() {
    final base = AuraTypography.body.copyWith(
      color: AuraColors.textPrimary,
      height: 1.5,
      fontSize: 14,
    );
    final emphasis = base.copyWith(
      color: AuraColors.primaryAction,
      fontWeight: FontWeight.w700,
    );
    return MarkdownStyleSheet(
      p: base,
      strong: emphasis,
      em: base.copyWith(
        color: AuraColors.textSecondary,
        fontStyle: FontStyle.italic,
      ),
      listBullet: base.copyWith(color: AuraColors.primaryAction),
      h1: emphasis.copyWith(fontSize: 18),
      h2: emphasis.copyWith(fontSize: 16),
      h3: base.copyWith(
        fontSize: 15,
        fontWeight: FontWeight.w600,
        color: AuraColors.primaryAction,
      ),
      blockquote: base.copyWith(color: AuraColors.textSecondary),
      blockquoteDecoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            color: AuraColors.primaryAction.withValues(alpha: 0.45),
            width: 3,
          ),
        ),
      ),
      blockquotePadding: const EdgeInsets.only(left: 12),
      code: base.copyWith(
        color: AuraColors.textPrimary,
        backgroundColor: AuraColors.background,
        fontSize: 13,
      ),
      codeblockDecoration: BoxDecoration(
        color: AuraColors.background,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
      ),
      horizontalRuleDecoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: AuraColors.textSecondary.withValues(alpha: 0.20),
          ),
        ),
      ),
      a: base.copyWith(
        color: AuraColors.primaryAction,
        decoration: TextDecoration.underline,
      ),
    );
  }
}
