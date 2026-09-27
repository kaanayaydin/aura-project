import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/category_labels.dart';
import '../core/color_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/outfit_favorite.dart';
import '../models/suggestion.dart';
import '../models/wardrobe_item.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../widgets/aura_image.dart';
import 'lookbook_screen.dart';

/// Favori kombinler + Lookbook arsivi (sekmeli).
class FavoritesScreen extends ConsumerStatefulWidget {
  const FavoritesScreen({super.key});

  @override
  ConsumerState<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends ConsumerState<FavoritesScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AuraColors.background,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 20, 28, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Aura', style: AuraTypography.h3),
                  const SizedBox(height: 6),
                  Text('Arşivin', style: AuraTypography.caption),
                  const SizedBox(height: 18),
                  TabBar(
                    controller: _tabs,
                    labelColor: AuraColors.primaryAction,
                    unselectedLabelColor: AuraColors.textSecondary,
                    indicatorColor: AuraColors.primaryAction,
                    tabs: const [
                      Tab(text: 'Favoriler'),
                      Tab(key: Key('lookbook-tab'), text: 'Lookbook'),
                    ],
                  ),
                ],
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _tabs,
                children: const [
                  _FavoritesTab(),
                  LookbookScreen(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FavoritesTab extends ConsumerWidget {
  const _FavoritesTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final favorites = ref.watch(favoritesProvider);
    final wardrobe = ref.watch(wardrobeProvider).valueOrNull ?? const [];

    return RefreshIndicator(
      color: AuraColors.primaryAction,
      backgroundColor: AuraColors.surfaceElevated,
      onRefresh: () => ref.read(favoritesProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(28, 22, 28, 40),
        children: [
          favorites.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 56),
              child: Center(
                child: CircularProgressIndicator(color: AuraColors.primaryAction),
              ),
            ),
            error: (error, _) => _ErrorBox(message: _friendly(error)),
            data: (items) {
              if (items.isEmpty) {
                return const _EmptyState();
              }
              return Column(
                children: [
                  for (var i = 0; i < items.length; i++) ...[
                    if (i > 0) const SizedBox(height: 18),
                    _FavoriteCard(
                      favorite: items[i],
                      wardrobe: wardrobe,
                      onDelete: () => _delete(context, ref, items[i].id),
                    ),
                  ],
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, int id) async {
    try {
      await ref.read(favoritesProvider.notifier).remove(id);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Favori silindi.')),
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
    return 'Favoriler yüklenemedi. Lütfen tekrar dene.';
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

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
            Icons.favorite_border,
            size: 40,
            color: AuraColors.primaryAction,
          ),
          const SizedBox(height: 16),
          Text('Henüz favori yok', style: AuraTypography.h3),
          const SizedBox(height: 10),
          Text(
            'Öneri ekranından bir kombin üretip “Kombini favorilere ekle” ile buraya kaydedebilirsin.',
            textAlign: TextAlign.center,
            style: AuraTypography.bodySecondary,
          ),
        ],
      ),
    );
  }
}

class _ErrorBox extends StatelessWidget {
  const _ErrorBox({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AuraColors.error.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
      ),
      child: Text(message, style: AuraTypography.body),
    );
  }
}

class _FavoriteCard extends StatelessWidget {
  const _FavoriteCard({
    required this.favorite,
    required this.wardrobe,
    required this.onDelete,
  });

  final OutfitFavorite favorite;
  final List<WardrobeItem> wardrobe;
  final VoidCallback onDelete;

  WardrobeItem? _item(int? id) {
    if (id == null) return null;
    for (final item in wardrobe) {
      if (item.id == id) return item;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final top = _item(favorite.topItemId);
    final bottom = _item(favorite.bottomItemId);
    final accessory = _item(favorite.accessoryItemId);
    final scorePct = (favorite.matchScore * 100).round();
    final harmonyPct = favorite.colorHarmonyScore == null
        ? null
        : (favorite.colorHarmonyScore! * 100).round();

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  favorite.vibe.isEmpty ? 'Kombin' : favorite.vibe,
                  style: AuraTypography.h3,
                ),
              ),
              IconButton(
                tooltip: 'Favoriden çıkar',
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline, color: AuraColors.error),
                visualDensity: VisualDensity.compact,
              ),
            ],
          ),
          if (favorite.summary.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(favorite.summary, style: AuraTypography.bodySecondary),
          ],
          const SizedBox(height: 16),
          _ScoreBadge(value: '$scorePct%'),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _MetaChip(label: 'Ortam', value: favorite.occasionLabel),
              if (favorite.seasonBand != null && favorite.seasonBand!.isNotEmpty)
                _MetaChip(
                  label: 'Mevsim',
                  value: seasonDisplayLabel(favorite.seasonBand!),
                ),
              _MetaChip(
                label: 'Renk',
                value: harmonyPct == null
                    ? favorite.colorHarmonyLabel
                    : '${favorite.colorHarmonyLabel} · $harmonyPct%',
              ),
              if (favorite.temperatureCelsius != null)
                _MetaChip(
                  label: 'Sıcaklık',
                  value: '${favorite.temperatureCelsius!.round()}°C',
                ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              _PieceThumb(label: 'Üst', item: top, fallbackId: favorite.topItemId),
              const SizedBox(width: 12),
              _PieceThumb(
                label: 'Alt',
                item: bottom,
                fallbackId: favorite.bottomItemId,
              ),
              const SizedBox(width: 12),
              _PieceThumb(
                label: 'Aksesuar',
                item: accessory,
                fallbackId: favorite.accessoryItemId,
              ),
            ],
          ),
          if (favorite.perfumeLabel != null &&
              favorite.perfumeLabel!.isNotEmpty) ...[
            const SizedBox(height: 16),
            Row(
              children: [
                const Icon(Icons.spa_outlined, size: 16, color: AuraColors.primaryAction),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(favorite.perfumeLabel!, style: AuraTypography.body),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _ScoreBadge extends StatelessWidget {
  const _ScoreBadge({required this.value});

  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: AuraColors.primaryAction,
        borderRadius: BorderRadius.circular(AuraRadii.pillRadius),
      ),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: 'Skor  ',
              style: AuraTypography.caption.copyWith(color: AuraColors.surface),
            ),
            TextSpan(
              text: value,
              style: AuraTypography.body.copyWith(
                color: AuraColors.surface,
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AuraColors.surface,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
      ),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(text: '$label  ', style: AuraTypography.caption),
            TextSpan(
              text: value,
              style: AuraTypography.caption.copyWith(
                color: AuraColors.textPrimary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PieceThumb extends StatelessWidget {
  const _PieceThumb({
    required this.label,
    required this.item,
    required this.fallbackId,
  });

  final String label;
  final WardrobeItem? item;
  final int? fallbackId;

  @override
  Widget build(BuildContext context) {
    final missing = item == null && fallbackId == null;
    final color = item?.color;
    final caption = item == null
        ? (fallbackId == null ? 'Yok' : '#$fallbackId')
        : [
            categoryDisplayLabel(item!.category),
            if (color != null && color.isNotEmpty) colorDisplayLabel(color),
          ].join(' · ');
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AuraTypography.caption),
          const SizedBox(height: 8),
          AspectRatio(
            aspectRatio: 0.85,
            child: missing
                ? Container(
                    decoration: BoxDecoration(
                      color: AuraColors.surface,
                      borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
                    ),
                    alignment: Alignment.center,
                    child: Text('—', style: AuraTypography.caption),
                  )
                : AuraImage(
                    bytes: item?.decodedBytes,
                    imageUrl: item?.displayUrl,
                    borderRadius: AuraRadii.cardRadius,
                  ),
          ),
          const SizedBox(height: 8),
          Text(
            caption,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: AuraTypography.caption.copyWith(color: AuraColors.textPrimary),
          ),
        ],
      ),
    );
  }
}
