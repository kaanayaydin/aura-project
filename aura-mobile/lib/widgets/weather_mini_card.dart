import 'package:flutter/material.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../core/weather_condition_labels.dart';
import '../models/weather_snapshot.dart';

/// Oneri ekraninin basindaki kompakt hava durumu karti.
class WeatherMiniCard extends StatelessWidget {
  const WeatherMiniCard({
    super.key,
    required this.weather,
    required this.autoEnabled,
    required this.onToggle,
    this.loading = false,
    this.onRefresh,
  });

  final WeatherSnapshot? weather;
  final bool autoEnabled;
  final ValueChanged<bool> onToggle;
  final bool loading;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
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
              const Icon(Icons.wb_cloudy_outlined, color: AuraColors.primaryAction),
              const SizedBox(width: 8),
              Expanded(
                child: Text('Hava durumu', style: AuraTypography.h3.copyWith(fontSize: 18)),
              ),
              if (onRefresh != null)
                IconButton(
                  tooltip: 'Yenile',
                  onPressed: loading ? null : onRefresh,
                  icon: loading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AuraColors.primaryAction,
                          ),
                        )
                      : const Icon(
                          Icons.refresh_rounded,
                          size: 20,
                          color: AuraColors.textSecondary,
                        ),
                ),
            ],
          ),
          const SizedBox(height: 10),
          if (weather != null) ...[
            Text(
              '${weather!.temperatureCelsius.round()}°C  ·  %${weather!.humidityPercent.round()} nem',
              style: AuraTypography.h2.copyWith(fontSize: 22),
            ),
            const SizedBox(height: 6),
            Text(
              '${weatherConditionDisplayLabel(weather!.condition)}  ·  ${weather!.locationName}  ·  ${weather!.sourceLabel}',
              style: AuraTypography.caption,
            ),
          ] else
            Text(
              loading ? 'Hava alınıyor...' : 'Hava henüz yüklenmedi.',
              style: AuraTypography.bodySecondary,
            ),
          const SizedBox(height: 8),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: Text(
              'Konumdan otomatik al',
              style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
            ),
            subtitle: Text(
              'Açıkken manuel kaydırıcılar yerine canlı veya tahmini hava kullanılır.',
              style: AuraTypography.caption,
            ),
            value: autoEnabled,
            activeThumbColor: AuraColors.primaryAction,
            activeTrackColor: AuraColors.primaryAction.withValues(alpha: 0.35),
            onChanged: onToggle,
          ),
        ],
      ),
    );
  }
}
