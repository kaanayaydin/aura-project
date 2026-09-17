import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../models/user_perfume.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';

/// Katalogdan favori secip kisisel parfum rafina ekleme ekrani.
class PerfumeShelfScreen extends ConsumerWidget {
  const PerfumeShelfScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shelf = ref.watch(perfumeShelfProvider);
    final catalog = ref.watch(perfumeCatalogProvider);

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          color: AuraTheme.champagne,
          backgroundColor: AuraTheme.carbonElevated,
          onRefresh: () async {
            await ref.read(perfumeShelfProvider.notifier).refresh();
            await ref.read(perfumeCatalogProvider.notifier).refresh();
          },
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
            children: [
              Text('Aura', style: Theme.of(context).textTheme.displayMedium),
              const SizedBox(height: 4),
              Text(
                'Parfum rafın',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.mistMuted,
                    ),
              ),
              const SizedBox(height: 24),
              _AccountSection(
                onLogout: () => _logout(context, ref),
              ),
              const SizedBox(height: 28),
              Text('Rafım', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 12),
              shelf.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                    child: CircularProgressIndicator(color: AuraTheme.champagne),
                  ),
                ),
                error: (error, _) => _ErrorText(message: _friendly(error)),
                data: (items) {
                  if (items.isEmpty) {
                    return Text(
                      'Henuz favori yok. Asagidaki katalogdan ekle.',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: AuraTheme.mistMuted,
                          ),
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
              Text('Katalog', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 4),
              Text(
                'Kuratorlu niche secim — dokunarak rafa ekle',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.mistMuted,
                      fontSize: 12,
                    ),
              ),
              const SizedBox(height: 12),
              catalog.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                    child: CircularProgressIndicator(color: AuraTheme.champagne),
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
          const SnackBar(content: Text('Parfum rafa eklendi.')),
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
          const SnackBar(content: Text('Parfum raftan cikarildi.')),
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

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AuraTheme.carbonElevated,
        title: const Text('Cikis yap'),
        content: const Text('Oturumun sonlandirilacak. Emin misin?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Vazgec'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AuraTheme.champagneGold),
            child: const Text('Cikis Yap'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await ref.read(authSessionProvider.notifier).logout();
  }

  String _friendly(Object error) {
    if (error is ApiException) return error.message;
    return 'Baglanti hatasi: backend 8080 ayakta mi?';
  }
}

class _AccountSection extends ConsumerWidget {
  const _AccountSection({required this.onLogout});

  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authSessionProvider);
    final label = session?.displayName?.isNotEmpty == true
        ? session!.displayName!
        : (session?.email.isNotEmpty == true
            ? session!.email
            : session?.username ?? 'Misafir');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AuraTheme.champagneGold.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Hesap', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                ),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: onLogout,
              icon: const Icon(Icons.logout, size: 18),
              style: OutlinedButton.styleFrom(
                foregroundColor: AuraTheme.champagneGold,
                side: const BorderSide(color: AuraTheme.champagneGold),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              label: const Text('Cikis Yap'),
            ),
          ),
        ],
      ),
    );
  }
}

class _ShelfTile extends StatelessWidget {
  const _ShelfTile({required this.perfume, this.onRemove});

  final UserPerfume perfume;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.25)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  perfume.displayTitle,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 15),
                ),
                const SizedBox(height: 4),
                Text(
                  perfume.chords.join(' · '),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 12,
                      ),
                ),
              ],
            ),
          ),
          if (onRemove != null)
            IconButton(
              onPressed: onRemove,
              icon: const Icon(Icons.remove_circle_outline, color: AuraTheme.danger),
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
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AuraTheme.carbonSoft),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  perfume.displayTitle,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 15),
                ),
              ),
              if (perfume.onShelf)
                const Text(
                  'Rafta',
                  style: TextStyle(
                    color: AuraTheme.champagne,
                    fontWeight: FontWeight.w700,
                    fontSize: 12,
                  ),
                )
              else
                TextButton(
                  onPressed: onAdd,
                  child: const Text('Ekle'),
                ),
            ],
          ),
          if (perfume.blurb != null) ...[
            const SizedBox(height: 6),
            Text(
              perfume.blurb!,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                    fontSize: 12,
                  ),
            ),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: perfume.chords
                .map(
                  (chord) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AuraTheme.carbonSoft,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      chord,
                      style: const TextStyle(
                        color: AuraTheme.champagne,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                      ),
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
    return Text(message, style: const TextStyle(color: AuraTheme.danger));
  }
}
