import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../core/category_labels.dart';
import '../core/color_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/orientation_choice.dart';
import '../models/wardrobe_upload_outcome.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../widgets/wardrobe_tile.dart';
import 'orientation_confirmation_screen.dart';
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
    final uploadPhase = ref.watch(wardrobeUploadPhaseProvider);
    final pendingSave = uploadPhase is WardrobeUploadSaving ? uploadPhase : null;

    return Scaffold(
      backgroundColor: AuraColors.background,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 18, 16, 12),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Aura', style: AuraTypography.h3),
                        const SizedBox(height: 6),
                        Text('Sanal dolabın', style: AuraTypography.caption),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Yenile',
                    onPressed: () => ref.read(wardrobeProvider.notifier).refresh(),
                    icon: const Icon(
                      Icons.refresh_rounded,
                      color: AuraColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: wardrobe.when(
                loading: () => const Center(
                  child: CircularProgressIndicator(color: AuraColors.primaryAction),
                ),
                error: (error, _) => _ErrorPane(
                  message: _friendlyError(error),
                  onRetry: () => ref.read(wardrobeProvider.notifier).refresh(),
                ),
                data: (items) {
                  if (items.isEmpty && pendingSave == null) {
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
                  final showPending = pendingSave != null;
                  final gridCount = filtered.length + (showPending ? 1 : 0);

                  return Column(
                    children: [
                      _FilterBar(
                        label: 'Kategori',
                        options: categories,
                        selected: _categoryFilter,
                        display: categoryDisplayLabel,
                        onSelected: (value) => setState(() => _categoryFilter = value),
                      ),
                      if (colors.isNotEmpty)
                        _FilterBar(
                          label: 'Renk',
                          options: colors,
                          selected: _colorFilter,
                          display: colorDisplayLabel,
                          onSelected: (value) => setState(() => _colorFilter = value),
                        ),
                      Expanded(
                        child: RefreshIndicator(
                          color: AuraColors.primaryAction,
                          backgroundColor: AuraColors.surfaceElevated,
                          onRefresh: () =>
                              ref.read(wardrobeProvider.notifier).refresh(),
                          child: gridCount == 0
                              ? ListView(
                                  children: const [
                                    SizedBox(height: 80),
                                    Center(
                                      child: Text(
                                        'Filtreye uyan parça yok.',
                                        style: TextStyle(color: AuraColors.textSecondary),
                                      ),
                                    ),
                                  ],
                                )
                              : GridView.builder(
                                  padding: const EdgeInsets.fromLTRB(22, 16, 22, 112),
                                  gridDelegate:
                                      const SliverGridDelegateWithFixedCrossAxisCount(
                                    crossAxisCount: 2,
                                    mainAxisSpacing: 16,
                                    crossAxisSpacing: 16,
                                    childAspectRatio: 0.72,
                                  ),
                                  itemCount: gridCount,
                                  itemBuilder: (_, index) {
                                    if (showPending && index == 0) {
                                      return _PendingWardrobeTile(
                                        bytes: pendingSave.previewBytes,
                                        category: pendingSave.category,
                                      );
                                    }
                                    final item = filtered[showPending ? index - 1 : index];
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
        backgroundColor: AuraColors.primaryAction,
        foregroundColor: AuraColors.surface,
        icon: const Icon(Icons.add_a_photo_outlined),
        label: const Text('Fotoğraf ekle'),
      ),
    );
  }

  Future<void> _showUploadSheet(BuildContext context) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: AuraColors.surfaceElevated,
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
                    color: AuraColors.textSecondary.withValues(alpha: 0.35),
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                const SizedBox(height: 12),
                ListTile(
                  leading: const Icon(
                    Icons.photo_library_outlined,
                    color: AuraColors.textPrimary,
                  ),
                  title: Text('Galeriden seç', style: AuraTypography.body),
                  onTap: () => Navigator.pop(context, ImageSource.gallery),
                ),
                ListTile(
                  leading: const Icon(
                    Icons.photo_camera_outlined,
                    color: AuraColors.textPrimary,
                  ),
                  title: Text('Kamera ile çek', style: AuraTypography.body),
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
        child: CircularProgressIndicator(color: AuraColors.surface),
      ),
    );

    try {
      final outcome =
          await ref.read(wardrobeProvider.notifier).uploadFromSource(source);
      if (!context.mounted) return;
      Navigator.of(context, rootNavigator: true).pop();

      switch (outcome) {
        case WardrobeUploadCancelled():
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('İptal edildi.')),
          );
        case WardrobeUploadDone(:final message):
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(message)),
          );
        case WardrobeUploadNeedsConfirmation(:final pending):
          final decision =
              await Navigator.of(context).push<OrientationConfirmDecision>(
            MaterialPageRoute(
              builder: (_) => OrientationConfirmationScreen(
                result: pending.normalize,
              ),
            ),
          );
          if (!context.mounted) return;
          if (decision == null) {
            ref.read(wardrobeProvider.notifier).cancelOrientationConfirmation();
            return;
          }
          try {
            final message = await ref
                .read(wardrobeProvider.notifier)
                .completeConfirmedUpload(
                  pending: pending,
                  decision: decision,
                );
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(message)),
              );
            }
          } catch (error) {
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(_friendlyError(error))),
              );
            }
          }
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
    return 'Bağlantı hatası. Lütfen tekrar dene.';
  }
}

class _PendingWardrobeTile extends StatelessWidget {
  const _PendingWardrobeTile({
    required this.bytes,
    required this.category,
  });

  final Uint8List bytes;
  final String category;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('pending-wardrobe-thumb'),
      decoration: BoxDecoration(
        color: AuraColors.surface,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.memory(bytes, fit: BoxFit.cover, gaplessPlayback: true),
                ColoredBox(
                  color: AuraColors.primaryAction.withValues(alpha: 0.45),
                  child: const Center(
                    child: SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AuraColors.surface,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  categoryDisplayLabel(category),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 4),
                Text('kaydediliyor…', style: AuraTypography.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterBar extends StatelessWidget {
  const _FilterBar({
    required this.label,
    required this.options,
    required this.selected,
    required this.onSelected,
    this.display,
  });

  final String label;
  final List<String> options;
  final String? selected;
  final ValueChanged<String?> onSelected;

  /// Çip üstündeki yazı. Karşılaştırma her zaman ham [options] değeriyle kalır.
  final String Function(String value)? display;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 22),
        children: [
          Padding(
            padding: const EdgeInsets.only(right: 10, top: 16),
            child: Text(label, style: AuraTypography.caption),
          ),
          _chip(
            label: 'Tümü',
            isSelected: selected == null,
            onTap: () => onSelected(null),
          ),
          ...options.map(
            (option) => _chip(
              label: display?.call(option) ?? option,
              isSelected: selected == option,
              onTap: () => onSelected(selected == option ? null : option),
            ),
          ),
        ],
      ),
    );
  }

  Widget _chip({
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        label: Text(label),
        selected: isSelected,
        onSelected: (_) => onTap(),
        showCheckmark: false,
        selectedColor: AuraColors.primaryAction,
        backgroundColor: AuraColors.surface,
        side: BorderSide.none,
        labelStyle: TextStyle(
          color: isSelected ? AuraColors.surface : AuraColors.textSecondary,
          fontWeight: FontWeight.w600,
          fontSize: 12,
        ),
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
        padding: const EdgeInsets.all(28),
        child: Container(
          padding: const EdgeInsets.all(32),
          decoration: BoxDecoration(
            color: AuraColors.surfaceElevated,
            borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
            boxShadow: AuraShadows.cardShadow,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.checkroom_outlined,
                size: 56,
                color: AuraColors.textSecondary,
              ),
              const SizedBox(height: 18),
              Text('Dolap boş', style: AuraTypography.h3),
              const SizedBox(height: 10),
              Text(
                'Bir kıyafet fotoğrafı ekle; analiz edip dolabına eklensin.',
                textAlign: TextAlign.center,
                style: AuraTypography.bodySecondary,
              ),
            ],
          ),
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
            Text(
              message,
              textAlign: TextAlign.center,
              style: AuraTypography.body,
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: onRetry,
              style: ElevatedButton.styleFrom(
                backgroundColor: AuraColors.primaryAction,
                foregroundColor: AuraColors.surface,
              ),
              child: const Text('Tekrar dene'),
            ),
          ],
        ),
      ),
    );
  }
}
