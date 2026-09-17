import 'package:flutter/material.dart';

import '../core/theme.dart';
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
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF243038), Color(0xFF1A1E22)],
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.22)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.wb_cloudy_outlined, color: AuraTheme.champagne),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Hava durumu',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 16),
                ),
              ),
              if (onRefresh != null)
                IconButton(
                  tooltip: 'Yenile',
                  onPressed: loading ? null : onRefresh,
                  icon: loading
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.refresh_rounded, size: 20),
                ),
            ],
          ),
          const SizedBox(height: 8),
          if (weather != null) ...[
            Text(
              '${weather!.temperatureCelsius.round()}°C  ·  %${weather!.humidityPercent.round()} nem',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: AuraTheme.champagne,
                    fontSize: 22,
                  ),
            ),
            const SizedBox(height: 4),
            Text(
              '${weather!.condition}  ·  ${weather!.locationName}  ·  ${weather!.sourceLabel}',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                    fontSize: 12,
                  ),
            ),
          ] else
            Text(
              loading ? 'Hava aliniyor...' : 'Hava henuz yuklenmedi.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                  ),
            ),
          const SizedBox(height: 12),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text(
              'Konumdan otomatik al',
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
            ),
            subtitle: const Text(
              'Acikken manuel kaydiricilar yerine canli/tahmini hava kullanilir.',
              style: TextStyle(color: AuraTheme.mistMuted, fontSize: 11),
            ),
            value: autoEnabled,
            activeThumbColor: AuraTheme.champagne,
            onChanged: onToggle,
          ),
        ],
      ),
    );
  }
}
