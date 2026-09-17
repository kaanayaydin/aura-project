import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
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
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 12, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Aura AI',
                          style: Theme.of(context).textTheme.displayMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          chat.lastWeatherSummary ??
                              'Baş stilistin — karbon & şampanya',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    color: AuraTheme.mistMuted,
                                    fontSize: 12,
                                  ),
                        ),
                      ],
                    ),
                  ),
                  if (chat.messages.isNotEmpty)
                    IconButton(
                      tooltip: 'Sohbeti temizle',
                      onPressed: () => ref.read(chatProvider.notifier).clear(),
                      icon: const Icon(Icons.refresh, color: AuraTheme.mistMuted),
                    ),
                ],
              ),
            ),
            if (chat.lastSource != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 10, 20, 0),
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
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
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
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AuraTheme.carbonSoft,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        isLive ? 'Ollama · canlı' : 'Yedek stilist',
        style: const TextStyle(
          color: AuraTheme.champagne,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
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
      'Raftan hangi niche koku bu sahneye uyar?',
    ];
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 28, 20, 20),
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF252A30), Color(0xFF1A1D21)],
            ),
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.22)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Merhaba — ben Aura.',
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      color: AuraTheme.champagne,
                    ),
              ),
              const SizedBox(height: 10),
              Text(
                'Baş stilistin. Dolabın, niche rafın ve günün havasını tek bir imajda eritirim — nokta atışı, sofistike, gereksiz gürültü yok.',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.mistMuted,
                      height: 1.45,
                    ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        Text(
          'Hızlı başlangıç',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        ...prompts.map(
          (prompt) => Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: OutlinedButton(
              onPressed: () => onPrompt(prompt),
              style: OutlinedButton.styleFrom(
                alignment: Alignment.centerLeft,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
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
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AuraTheme.carbonElevated,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AuraTheme.carbonSoft),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: AuraTheme.champagne,
              ),
            ),
            SizedBox(width: 10),
            Text(
              'Aura düşünüyor…',
              style: TextStyle(color: AuraTheme.mistMuted, fontSize: 13),
            ),
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
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
      decoration: const BoxDecoration(
        color: AuraTheme.carbonElevated,
        border: Border(top: BorderSide(color: AuraTheme.carbonSoft)),
      ),
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
              onSubmitted: (_) {
                if (enabled) onSend();
              },
              decoration: const InputDecoration(
                hintText: 'Stilistine sor…',
                contentPadding:
                    EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              ),
            ),
          ),
          const SizedBox(width: 10),
          FilledButton(
            onPressed: enabled ? onSend : null,
            style: FilledButton.styleFrom(
              backgroundColor: AuraTheme.champagne,
              foregroundColor: AuraTheme.carbon,
              shape: const CircleBorder(),
              padding: const EdgeInsets.all(14),
            ),
            child: const Icon(Icons.arrow_upward_rounded, size: 22),
          ),
        ],
      ),
    );
  }
}
