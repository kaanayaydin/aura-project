import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../providers/providers.dart';
import '../widgets/chat_bubble.dart';

/// Lokal LLM destekli Aura stilist sohbeti.
class AuraChatScreen extends ConsumerStatefulWidget {
  const AuraChatScreen({super.key});

  @override
  ConsumerState<AuraChatScreen> createState() => _AuraChatScreenState();
}

class _AuraChatScreenState extends ConsumerState<AuraChatScreen> {
  final _controller = TextEditingController();
  final _scroll = ScrollController();
  final _focus = FocusNode();

  @override
  void dispose() {
    _controller.dispose();
    _scroll.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.animateTo(
        _scroll.position.maxScrollExtent + 80,
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
      );
    });
  }

  Future<void> _send() async {
    final text = _controller.text;
    _controller.clear();
    await ref.read(chatProvider.notifier).send(text);
    _scrollToEnd();
  }

  @override
  Widget build(BuildContext context) {
    final chat = ref.watch(chatProvider);
    ref.listen<ChatState>(chatProvider, (previous, next) {
      if (previous?.messages.length != next.messages.length ||
          previous?.sending != next.sending) {
        _scrollToEnd();
      }
      if (next.error != null && next.error != previous?.error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.error!)),
        );
      }
    });

    return Scaffold(
      backgroundColor: AuraColors.background,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 20, 16, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Aura AI', style: AuraTypography.h3),
                        const SizedBox(height: 6),
                        Text(
                          chat.lastWeatherSummary ??
                              'Dolabını ve günün havasını tek bir imaja çevirir.',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: AuraTypography.caption,
                        ),
                      ],
                    ),
                  ),
                  if (chat.messages.isNotEmpty)
                    IconButton(
                      tooltip: 'Sohbeti temizle',
                      onPressed: () => ref.read(chatProvider.notifier).clear(),
                      icon: const Icon(
                        Icons.refresh,
                        color: AuraColors.textSecondary,
                      ),
                    ),
                ],
              ),
            ),
            if (chat.lastSource != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(28, 14, 28, 0),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: _SourceChip(source: chat.lastSource!),
                ),
              ),
            Expanded(
              child: chat.messages.isEmpty && !chat.sending
                  ? _EmptyChat(
                      onPrompt: (prompt) =>
                          ref.read(chatProvider.notifier).send(prompt),
                    )
                  : ListView.builder(
                      controller: _scroll,
                      padding: const EdgeInsets.fromLTRB(22, 24, 22, 20),
                      itemCount: chat.messages.length + (chat.sending ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index >= chat.messages.length) {
                          return const _TypingBubble();
                        }
                        return ChatBubble(message: chat.messages[index]);
                      },
                    ),
            ),
            _Composer(
              controller: _controller,
              focusNode: _focus,
              enabled: !chat.sending,
              onSend: _send,
            ),
          ],
        ),
      ),
    );
  }
}

class _SourceChip extends StatelessWidget {
  const _SourceChip({required this.source});

  final String source;

  @override
  Widget build(BuildContext context) {
    final isLive = source == 'ollama';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: AuraColors.surface,
        borderRadius: BorderRadius.circular(AuraRadii.pillRadius),
      ),
      child: Text(
        isLive ? 'Canlı stilist' : 'Yedek stilist',
        style: AuraTypography.caption,
      ),
    );
  }
}

class _EmptyChat extends StatelessWidget {
  const _EmptyChat({required this.onPrompt});

  final ValueChanged<String> onPrompt;

  @override
  Widget build(BuildContext context) {
    final prompts = [
      'Bugün nasıl bir imaj çizeyim?',
      'Toplantı için sessiz bir güç kombini?',
      'Raftan hangi niş koku bu sahneye uyar?',
    ];
    return ListView(
      padding: const EdgeInsets.fromLTRB(28, 40, 28, 32),
      children: [
        Container(
          padding: const EdgeInsets.all(28),
          decoration: BoxDecoration(
            color: AuraColors.surfaceElevated,
            borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
            boxShadow: AuraShadows.cardShadow,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Merhaba — ben Aura.', style: AuraTypography.h2),
              const SizedBox(height: 14),
              Text(
                'Baş stilistin. Dolabın, niş rafın ve günün havasını tek bir imajda eritirim — nokta atışı, sofistike, gereksiz gürültü yok.',
                style: AuraTypography.bodySecondary,
              ),
            ],
          ),
        ),
        const SizedBox(height: 32),
        Text('Hızlı başlangıç', style: AuraTypography.h3),
        const SizedBox(height: 16),
        ...prompts.map(
          (prompt) => Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: OutlinedButton(
              onPressed: () => onPrompt(prompt),
              style: OutlinedButton.styleFrom(
                alignment: Alignment.centerLeft,
                foregroundColor: AuraColors.textPrimary,
                backgroundColor: AuraColors.surfaceElevated,
                side: BorderSide(
                  color: AuraColors.textSecondary.withValues(alpha: 0.35),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 16,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
                ),
                textStyle: AuraTypography.body,
              ),
              child: Text(prompt),
            ),
          ),
        ),
      ],
    );
  }
}

class _TypingBubble extends StatelessWidget {
  const _TypingBubble();

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AuraColors.surfaceElevated,
          borderRadius: BorderRadius.circular(18),
          boxShadow: AuraShadows.cardShadow,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: AuraColors.primaryAction,
              ),
            ),
            const SizedBox(width: 10),
            Text('Aura düşünüyor…', style: AuraTypography.caption),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  const _Composer({
    required this.controller,
    required this.focusNode,
    required this.enabled,
    required this.onSend,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool enabled;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    final fieldRadius = BorderRadius.circular(AuraRadii.pillRadius);
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 22),
      color: AuraColors.background,
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              enabled: enabled,
              minLines: 1,
              maxLines: 4,
              textInputAction: TextInputAction.send,
              style: AuraTypography.body,
              cursorColor: AuraColors.primaryAction,
              onSubmitted: (_) {
                if (enabled) onSend();
              },
              decoration: InputDecoration(
                hintText: 'Stilistine sor…',
                hintStyle: AuraTypography.caption,
                filled: true,
                fillColor: AuraColors.surfaceElevated,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 16,
                ),
                border: OutlineInputBorder(
                  borderRadius: fieldRadius,
                  borderSide: BorderSide.none,
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: fieldRadius,
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: fieldRadius,
                  borderSide: const BorderSide(color: AuraColors.primaryAction),
                ),
                disabledBorder: OutlineInputBorder(
                  borderRadius: fieldRadius,
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          FilledButton(
            onPressed: enabled ? onSend : null,
            style: FilledButton.styleFrom(
              backgroundColor: AuraColors.primaryAction,
              foregroundColor: AuraColors.surface,
              disabledBackgroundColor:
                  AuraColors.primaryAction.withValues(alpha: 0.38),
              disabledForegroundColor:
                  AuraColors.surface.withValues(alpha: 0.7),
              shape: const CircleBorder(),
              padding: const EdgeInsets.all(16),
            ),
            child: const Icon(Icons.arrow_upward_rounded, size: 22),
          ),
        ],
      ),
    );
  }
}
