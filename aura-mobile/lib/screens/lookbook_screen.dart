import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/category_labels.dart';
import '../core/color_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
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
      color: AuraColors.primaryAction,
      backgroundColor: AuraColors.surfaceElevated,
      onRefresh: () => ref.read(lookbookProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(28, 16, 28, 40),
        children: [
          lookbook.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 56),
              child: Center(
                child: CircularProgressIndicator(color: AuraColors.primaryAction),
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
                    if (i > 0) const SizedBox(height: 18),
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
    return 'Lookbook yüklenemedi. Lütfen tekrar dene.';
  }
}

class _EmptyLookbook extends StatelessWidget {
  const _EmptyLookbook();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 44, horizontal: 24),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Column(
        children: [
          const Icon(
            Icons.auto_stories_outlined,
            size: 40,
            color: AuraColors.primaryAction,
          ),
          const SizedBox(height: 16),
          Text('Lookbook boş', style: AuraTypography.h3),
          const SizedBox(height: 10),
          Text(
            'Sanal deneme sonucunu Lookbook\'a ekleyerek burada sakla.',
            textAlign: TextAlign.center,
            style: AuraTypography.bodySecondary,
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
      if (entry.category != null && entry.category!.isNotEmpty)
        categoryDisplayLabel(entry.category!),
      if (entry.color != null && entry.color!.isNotEmpty)
        colorDisplayLabel(entry.color!),
    ].join(' · ');
    final dateLabel = entry.createdAt == null
        ? ''
        : '${entry.createdAt!.day.toString().padLeft(2, '0')}.'
            '${entry.createdAt!.month.toString().padLeft(2, '0')}.'
            '${entry.createdAt!.year}';

    return Container(
      key: Key('lookbook-card-${entry.jobId}'),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
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
                        errorBuilder: (_, _, _) => Container(
                          color: AuraColors.surface,
                          alignment: Alignment.center,
                          child: const Icon(
                            Icons.broken_image_outlined,
                            color: AuraColors.textSecondary,
                          ),
                        ),
                      )
                    : Container(color: AuraColors.surface)),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.isEmpty ? 'Deneme #${entry.jobId}' : title,
                  style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
                ),
                if (dateLabel.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(dateLabel, style: AuraTypography.caption),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AuraColors.error.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
      ),
      child: Text(message, style: AuraTypography.body),
    );
  }
}
