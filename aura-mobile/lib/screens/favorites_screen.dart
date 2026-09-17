import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../models/outfit_favorite.dart';
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
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Aura', style: Theme.of(context).textTheme.displayMedium),
                  const SizedBox(height: 4),
                  Text(
                    'Arşivin',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: AuraTheme.mistMuted,
                        ),
                  ),
                  const SizedBox(height: 16),
                  TabBar(
                    controller: _tabs,
                    labelColor: AuraTheme.champagneGold,
                    unselectedLabelColor: AuraTheme.mistMuted,
                    indicatorColor: AuraTheme.champagneGold,
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
      color: AuraTheme.champagne,
      backgroundColor: AuraTheme.carbonElevated,
      onRefresh: () => ref.read(favoritesProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          favorites.when(
            loading: () => const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(
                child: CircularProgressIndicator(color: AuraTheme.champagne),
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
                    if (i > 0) const SizedBox(height: 14),
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
    return 'Favoriler yuklenemedi. Backend 8080 portunda mi?';
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

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
            Icons.favorite_border,
            size: 40,
            color: AuraTheme.champagne.withValues(alpha: 0.7),
          ),
          const SizedBox(height: 14),
          Text(
            'Henuz favori yok',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Oneri ekranindan bir kombin uretip “Kombini Favorilere Ekle” ile buraya kaydedebilirsin.',
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

class _ErrorBox extends StatelessWidget {
  const _ErrorBox({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AuraTheme.danger.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(message),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF252A30), Color(0xFF1A1D21)],
        ),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.22)),
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
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: AuraTheme.champagne,
                        fontSize: 20,
                      ),
                ),
              ),
              IconButton(
                tooltip: 'Favoriden cikar',
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline, color: AuraTheme.danger),
                visualDensity: VisualDensity.compact,
              ),
            ],
          ),
          if (favorite.summary.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              favorite.summary,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                  ),
            ),
          ],
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _Chip(label: 'Skor', value: '$scorePct%'),
              _Chip(label: 'Ortam', value: favorite.occasionLabel),
              if (favorite.seasonBand != null && favorite.seasonBand!.isNotEmpty)
                _Chip(label: 'Mevsim', value: favorite.seasonBand!),
              _Chip(
                label: 'Renk',
                value: harmonyPct == null
                    ? favorite.colorHarmonyLabel
                    : '${favorite.colorHarmonyLabel} · $harmonyPct%',
              ),
              if (favorite.temperatureCelsius != null)
                _Chip(
                  label: 'Sicaklik',
                  value: '${favorite.temperatureCelsius!.round()}°C',
                ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              _PieceThumb(label: 'Ust', item: top, fallbackId: favorite.topItemId),
              const SizedBox(width: 10),
              _PieceThumb(
                label: 'Alt',
                item: bottom,
                fallbackId: favorite.bottomItemId,
              ),
              const SizedBox(width: 10),
              _PieceThumb(
                label: 'Aksesuar',
                item: accessory,
                fallbackId: favorite.accessoryItemId,
              ),
            ],
          ),
          if (favorite.perfumeLabel != null &&
              favorite.perfumeLabel!.isNotEmpty) ...[
            const SizedBox(height: 14),
            Row(
              children: [
                const Icon(Icons.spa_outlined, size: 16, color: AuraTheme.champagne),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    favorite.perfumeLabel!,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: AuraTheme.mist,
                          fontSize: 13,
                        ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: AuraTheme.carbonSoft.withValues(alpha: 0.85),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '$label  ',
              style: const TextStyle(
                color: AuraTheme.mistMuted,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
            TextSpan(
              text: value,
              style: const TextStyle(
                color: AuraTheme.mist,
                fontSize: 12,
                fontWeight: FontWeight.w700,
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
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
          ),
          const SizedBox(height: 6),
          AspectRatio(
            aspectRatio: 0.85,
            child: missing
                ? Container(
                    decoration: BoxDecoration(
                      color: AuraTheme.carbonSoft,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    alignment: Alignment.center,
                    child: const Text(
                      '—',
                      style: TextStyle(color: AuraTheme.mistMuted),
                    ),
                  )
                : AuraImage(
                    bytes: item?.decodedBytes,
                    borderRadius: 14,
                  ),
          ),
          const SizedBox(height: 6),
          Text(
            item?.category ?? (fallbackId == null ? 'Yok' : '#$fallbackId'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  fontSize: 11,
                  color: AuraTheme.mistMuted,
                ),
          ),
        ],
      ),
    );
  }
}
