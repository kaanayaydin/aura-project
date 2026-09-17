import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../models/vton_lookbook_entry.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../widgets/aura_image.dart';

/// Kaydedilmiş sanal deneme arşivi.
class LookbookScreen extends ConsumerWidget {
  const LookbookScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lookbook = ref.watch(lookbookProvider);

    return RefreshIndicator(
      color: AuraTheme.champagneGold,
      backgroundColor: AuraTheme.carbonElevated,
      onRefresh: () => ref.read(lookbookProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          lookbook.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(
                child: CircularProgressIndicator(color: AuraTheme.champagneGold),
              ),
            ),
            error: (error, _) => _ErrorBox(message: _friendly(error)),
            data: (items) {
              if (items.isEmpty) {
                return const _EmptyLookbook();
              }
              return Column(
                children: [
                  for (var i = 0; i < items.length; i++) ...[
                    if (i > 0) const SizedBox(height: 14),
                    _LookbookCard(entry: items[i]),
                  ],
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  String _friendly(Object error) {
    if (error is ApiException) return error.message;
    return 'Lookbook yuklenemedi. Backend 8080 portunda mi?';
  }
}

class _EmptyLookbook extends StatelessWidget {
  const _EmptyLookbook();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AuraTheme.carbonSoft),
      ),
      child: Column(
        children: [
          Icon(
            Icons.auto_stories_outlined,
            size: 40,
            color: AuraTheme.champagneGold.withValues(alpha: 0.75),
          ),
          const SizedBox(height: 14),
          Text(
            'Lookbook bos',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Sanal deneme sonucunu Lookbook\'a ekleyerek burada sakla.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                ),
          ),
        ],
      ),
    );
  }
}

class _LookbookCard extends ConsumerWidget {
  const _LookbookCard({required this.entry});

  final VtonLookbookEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final api = ref.read(apiServiceProvider);
    final bytes = _decode(entry.resultImageBase64);
    final title = [
      if (entry.category != null && entry.category!.isNotEmpty) entry.category!,
      if (entry.color != null && entry.color!.isNotEmpty) entry.color!,
    ].join(' · ');
    final dateLabel = entry.createdAt == null
        ? ''
        : '${entry.createdAt!.day.toString().padLeft(2, '0')}.'
            '${entry.createdAt!.month.toString().padLeft(2, '0')}.'
            '${entry.createdAt!.year}';

    return Container(
      key: Key('lookbook-card-${entry.jobId}'),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: AuraTheme.champagneGold.withValues(alpha: 0.28),
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AspectRatio(
            aspectRatio: 3 / 4,
            child: bytes != null
                ? AuraImage(bytes: bytes, borderRadius: 0)
                : (entry.resultImageUrl != null
                    ? Image.network(
                        api.absoluteVtonResultUrl(entry.resultImageUrl),
                        headers: api.vtonImageHeaders(entry.userId),
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => Container(
                          color: AuraTheme.carbonSoft,
                          alignment: Alignment.center,
                          child: const Icon(
                            Icons.broken_image_outlined,
                            color: AuraTheme.mistMuted,
                          ),
                        ),
                      )
                    : Container(color: AuraTheme.carbonSoft)),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.isEmpty ? 'Deneme #${entry.jobId}' : title,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (dateLabel.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    dateLabel,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: AuraTheme.mistMuted,
                          fontSize: 12,
                        ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Uint8List? _decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      return base64Decode(raw);
    } catch (_) {
      return null;
    }
  }
}

class _ErrorBox extends StatelessWidget {
  const _ErrorBox({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AuraTheme.danger.withValues(alpha: 0.4)),
      ),
      child: Text(message, style: TextStyle(color: AuraTheme.danger)),
    );
  }
}
