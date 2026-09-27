import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/accord_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/user_perfume.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import 'settings_screen.dart';

/// Katalogdan favori secip kisisel parfum rafina ekleme ekrani.
class PerfumeShelfScreen extends ConsumerWidget {
  const PerfumeShelfScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shelf = ref.watch(perfumeShelfProvider);
    final catalog = ref.watch(perfumeCatalogProvider);

    return Scaffold(
      backgroundColor: AuraColors.background,
      body: SafeArea(
        child: RefreshIndicator(
          color: AuraColors.primaryAction,
          backgroundColor: AuraColors.surfaceElevated,
          onRefresh: () async {
            await ref.read(perfumeShelfProvider.notifier).refresh();
            await ref.read(perfumeCatalogProvider.notifier).refresh();
          },
          child: ListView(
            padding: const EdgeInsets.fromLTRB(28, 18, 16, 40),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Aura', style: AuraTypography.h3),
                        const SizedBox(height: 6),
                        Text('Parfüm rafın', style: AuraTypography.caption),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Ayarlar',
                    onPressed: () {
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const SettingsScreen(),
                        ),
                      );
                    },
                    icon: const Icon(
                      Icons.settings_outlined,
                      color: AuraColors.textSecondary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 28),
              Text('Rafım', style: AuraTypography.h3),
              const SizedBox(height: 12),
              shelf.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 28),
                  child: Center(
                    child: CircularProgressIndicator(color: AuraColors.primaryAction),
                  ),
                ),
                error: (error, _) => _ErrorText(message: _friendly(error)),
                data: (items) {
                  if (items.isEmpty) {
                    return Text(
                      'Henüz favori yok. Aşağıdaki katalogdan ekle.',
                      style: AuraTypography.bodySecondary,
                    );
                  }
                  return Column(
                    children: items
                        .map(
                          (perfume) => _ShelfTile(
                            perfume: perfume,
                            onRemove: perfume.id == null
                                ? null
                                : () => _remove(context, ref, perfume.id!),
                          ),
                        )
                        .toList(),
                  );
                },
              ),
              const SizedBox(height: 28),
              Text('Katalog', style: AuraTypography.h3),
              const SizedBox(height: 6),
              Text(
                'Küratörlü niş seçim — dokunarak rafa ekle',
                style: AuraTypography.caption,
              ),
              const SizedBox(height: 12),
              catalog.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 28),
                  child: Center(
                    child: CircularProgressIndicator(color: AuraColors.primaryAction),
                  ),
                ),
                error: (error, _) => _ErrorText(message: _friendly(error)),
                data: (items) => Column(
                  children: items
                      .map(
                        (perfume) => _CatalogTile(
                          perfume: perfume,
                          onAdd: perfume.onShelf
                              ? null
                              : () => _add(context, ref, perfume.catalogId),
                        ),
                      )
                      .toList(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _add(BuildContext context, WidgetRef ref, String catalogId) async {
    try {
      await ref.read(perfumeCatalogProvider.notifier).addToShelf(catalogId);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Parfüm rafa eklendi.')),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_friendly(error))),
        );
      }
    }
  }

  Future<void> _remove(BuildContext context, WidgetRef ref, int id) async {
    try {
      await ref.read(perfumeShelfProvider.notifier).remove(id);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Parfüm raftan çıkarıldı.')),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_friendly(error))),
        );
      }
    }
  }

  String _friendly(Object error) {
    if (error is ApiException) return error.message;
    return 'Bağlantı kurulamadı. Lütfen tekrar dene.';
  }
}

class _ShelfTile extends StatelessWidget {
  const _ShelfTile({required this.perfume, this.onRemove});

  final UserPerfume perfume;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  perfume.displayTitle,
                  style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                Text(
                  perfume.chords.map(accordDisplayLabel).join(' · '),
                  style: AuraTypography.caption,
                ),
              ],
            ),
          ),
          if (onRemove != null)
            IconButton(
              onPressed: onRemove,
              icon: const Icon(Icons.remove_circle_outline, color: AuraColors.error),
            ),
        ],
      ),
    );
  }
}

class _CatalogTile extends StatelessWidget {
  const _CatalogTile({required this.perfume, this.onAdd});

  final UserPerfume perfume;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  perfume.displayTitle,
                  style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
                ),
              ),
              if (perfume.onShelf)
                Text(
                  'Rafta',
                  style: AuraTypography.caption.copyWith(
                    color: AuraColors.primaryAction,
                    fontWeight: FontWeight.w700,
                  ),
                )
              else
                TextButton(
                  onPressed: onAdd,
                  style: TextButton.styleFrom(
                    foregroundColor: AuraColors.primaryAction,
                  ),
                  child: const Text('Ekle'),
                ),
            ],
          ),
          if (perfume.blurb != null) ...[
            const SizedBox(height: 8),
            Text(perfume.blurb!, style: AuraTypography.caption),
          ],
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: perfume.chords
                .map(
                  (chord) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AuraColors.surface,
                      borderRadius: BorderRadius.circular(AuraRadii.pillRadius),
                    ),
                    child: Text(
                      accordDisplayLabel(chord),
                      style: AuraTypography.caption,
                    ),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _ErrorText extends StatelessWidget {
  const _ErrorText({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Text(message, style: AuraTypography.body.copyWith(color: AuraColors.error));
  }
}
