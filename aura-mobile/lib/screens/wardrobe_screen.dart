import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../core/theme.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../widgets/wardrobe_tile.dart';
import 'wardrobe_item_detail_screen.dart';

class WardrobeScreen extends ConsumerStatefulWidget {
  const WardrobeScreen({super.key});

  @override
  ConsumerState<WardrobeScreen> createState() => _WardrobeScreenState();
}

class _WardrobeScreenState extends ConsumerState<WardrobeScreen> {
  String? _categoryFilter;
  String? _colorFilter;

  @override
  Widget build(BuildContext context) {
    final wardrobe = ref.watch(wardrobeProvider);

    return Scaffold(
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Aura',
                          style: Theme.of(context).textTheme.displayMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Sanal dolabin',
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: AuraTheme.mistMuted,
                              ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Yenile',
                    onPressed: () => ref.read(wardrobeProvider.notifier).refresh(),
                    icon: const Icon(Icons.refresh_rounded),
                  ),
                ],
              ),
            ),
            Expanded(
              child: wardrobe.when(
                loading: () => const Center(
                  child: CircularProgressIndicator(color: AuraTheme.champagne),
                ),
                error: (error, _) => _ErrorPane(
                  message: _friendlyError(error),
                  onRetry: () => ref.read(wardrobeProvider.notifier).refresh(),
                ),
                data: (items) {
                  if (items.isEmpty) {
                    return const _EmptyWardrobe();
                  }
                  final categories = items
                      .map((item) => item.category)
                      .toSet()
                      .toList()
                    ..sort();
                  final colors = items
                      .map((item) => item.color)
                      .whereType<String>()
                      .where((color) => color.trim().isNotEmpty)
                      .toSet()
                      .toList()
                    ..sort();
                  final filtered = items.where((item) {
                    final categoryOk = _categoryFilter == null ||
                        item.category.toLowerCase() == _categoryFilter!.toLowerCase();
                    final colorOk = _colorFilter == null ||
                        (item.color?.toLowerCase() == _colorFilter!.toLowerCase());
                    return categoryOk && colorOk;
                  }).toList();

                  return Column(
                    children: [
                      _FilterBar(
                        label: 'Kategori',
                        options: categories,
                        selected: _categoryFilter,
                        onSelected: (value) => setState(() => _categoryFilter = value),
                      ),
                      if (colors.isNotEmpty)
                        _FilterBar(
                          label: 'Renk',
                          options: colors,
                          selected: _colorFilter,
                          onSelected: (value) => setState(() => _colorFilter = value),
                        ),
                      Expanded(
                        child: RefreshIndicator(
                          color: AuraTheme.champagne,
                          backgroundColor: AuraTheme.carbonElevated,
                          onRefresh: () =>
                              ref.read(wardrobeProvider.notifier).refresh(),
                          child: filtered.isEmpty
                              ? ListView(
                                  children: const [
                                    SizedBox(height: 80),
                                    Center(
                                      child: Text(
                                        'Filtreye uyan parca yok.',
                                        style: TextStyle(color: AuraTheme.mistMuted),
                                      ),
                                    ),
                                  ],
                                )
                              : GridView.builder(
                                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
                                  gridDelegate:
                                      const SliverGridDelegateWithFixedCrossAxisCount(
                                    crossAxisCount: 2,
                                    mainAxisSpacing: 12,
                                    crossAxisSpacing: 12,
                                    childAspectRatio: 0.72,
                                  ),
                                  itemCount: filtered.length,
                                  itemBuilder: (_, index) {
                                    final item = filtered[index];
                                    return GestureDetector(
                                      onTap: () {
                                        Navigator.of(context).push(
                                          MaterialPageRoute<void>(
                                            builder: (_) =>
                                                WardrobeItemDetailScreen(
                                              item: item,
                                            ),
                                          ),
                                        );
                                      },
                                      child: WardrobeTile(item: item),
                                    );
                                  },
                                ),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showUploadSheet(context),
        icon: const Icon(Icons.add_a_photo_outlined),
        label: const Text('Fotograf ekle'),
      ),
    );
  }

  Future<void> _showUploadSheet(BuildContext context) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: AuraTheme.carbonElevated,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 8, 8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AuraTheme.carbonSoft,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                const SizedBox(height: 12),
                ListTile(
                  leading: const Icon(Icons.photo_library_outlined),
                  title: const Text('Galeriden sec'),
                  onTap: () => Navigator.pop(context, ImageSource.gallery),
                ),
                ListTile(
                  leading: const Icon(Icons.photo_camera_outlined),
                  title: const Text('Kamera ile cek'),
                  onTap: () => Navigator.pop(context, ImageSource.camera),
                ),
              ],
            ),
          ),
        );
      },
    );
    if (source == null || !context.mounted) return;

    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: AuraTheme.champagne),
      ),
    );

    try {
      final message =
          await ref.read(wardrobeProvider.notifier).uploadFromSource(source);
      if (context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
      }
    } catch (error) {
      if (context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_friendlyError(error))),
        );
      }
    }
  }

  String _friendlyError(Object error) {
    if (error is ApiException) return error.message;
    return 'Baglanti hatasi: backend (8080) veya vision (8000) ayakta mi?';
  }
}

class _FilterBar extends StatelessWidget {
  const _FilterBar({
    required this.label,
    required this.options,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final List<String> options;
  final String? selected;
  final ValueChanged<String?> onSelected;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          Padding(
            padding: const EdgeInsets.only(right: 8, top: 10),
            child: Text(
              label,
              style: const TextStyle(
                color: AuraTheme.mistMuted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          FilterChip(
            label: const Text('Tumu'),
            selected: selected == null,
            onSelected: (_) => onSelected(null),
            selectedColor: AuraTheme.champagne.withValues(alpha: 0.22),
            labelStyle: TextStyle(
              color: selected == null ? AuraTheme.champagne : AuraTheme.mist,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
          const SizedBox(width: 6),
          ...options.map(
            (option) => Padding(
              padding: const EdgeInsets.only(right: 6),
              child: FilterChip(
                label: Text(option),
                selected: selected == option,
                onSelected: (_) =>
                    onSelected(selected == option ? null : option),
                selectedColor: AuraTheme.champagne.withValues(alpha: 0.22),
                labelStyle: TextStyle(
                  color: selected == option ? AuraTheme.champagne : AuraTheme.mist,
                  fontWeight: FontWeight.w600,
                  fontSize: 12,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyWardrobe extends StatelessWidget {
  const _EmptyWardrobe();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.checkroom_outlined, size: 56, color: AuraTheme.mistMuted),
            const SizedBox(height: 16),
            Text(
              'Dolap bos',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            Text(
              'Bir kiyafet fotografi ekle; Vision analiz edip dolaba yazar.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorPane extends StatelessWidget {
  const _ErrorPane({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: onRetry, child: const Text('Tekrar dene')),
          ],
        ),
      ),
    );
  }
}
