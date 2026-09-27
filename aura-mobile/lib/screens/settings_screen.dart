import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../providers/providers.dart';

/// Hesap ve oturum. Raf kataloğundan ayrı durur.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AuraColors.background,
      appBar: AppBar(
        backgroundColor: AuraColors.background,
        surfaceTintColor: Colors.transparent,
        foregroundColor: AuraColors.primaryAction,
        elevation: 0,
        title: Text('Ayarlar', style: AuraTypography.h3),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(28, 8, 28, 40),
        children: [
          _AccountSection(
            onLogout: () => _logout(context, ref),
          ),
        ],
      ),
    );
  }

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AuraColors.surfaceElevated,
        title: const Text('Çıkış yap'),
        content: const Text('Oturumun sonlandırılacak. Emin misin?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Vazgeç'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AuraColors.primaryAction),
            child: const Text('Çıkış Yap'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await ref.read(authSessionProvider.notifier).logout();
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
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Hesap', style: AuraTypography.h3),
          const SizedBox(height: 8),
          Text(label, style: AuraTypography.bodySecondary),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: onLogout,
              icon: const Icon(Icons.logout, size: 18),
              style: OutlinedButton.styleFrom(
                foregroundColor: AuraColors.primaryAction,
                side: const BorderSide(color: AuraColors.primaryAction),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              label: const Text('Çıkış Yap'),
            ),
          ),
        ],
      ),
    );
  }
}
