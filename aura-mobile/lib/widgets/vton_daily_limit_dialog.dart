import 'package:aura_mobile/core/theme.dart';
import 'package:flutter/material.dart';

/// Lüks Şampanya / Karbon — günlük VTON kota bilgilendirmesi.
Future<void> showVtonDailyLimitDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    barrierColor: AuraTheme.carbon.withValues(alpha: 0.72),
    builder: (ctx) {
      return AlertDialog(
        key: const Key('vton-daily-limit-dialog'),
        backgroundColor: AuraTheme.carbonElevated,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(
            color: AuraTheme.champagneGold.withValues(alpha: 0.45),
          ),
        ),
        title: Text(
          'Günlük deneme sınırına ulaşıldı',
          style: Theme.of(ctx).textTheme.titleLarge?.copyWith(
                color: AuraTheme.champagneGold,
                fontWeight: FontWeight.w600,
              ),
        ),
        content: Text(
          'Bugünkü sanal deneme hakkınız doldu. Yarın yeniden deneyebilirsiniz.',
          style: Theme.of(ctx).textTheme.bodyMedium?.copyWith(
                color: AuraTheme.mist,
              ),
        ),
        actions: [
          TextButton(
            key: const Key('vton-daily-limit-ok'),
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text(
              'Tamam',
              style: TextStyle(
                color: AuraTheme.champagneGold,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      );
    },
  );
}
