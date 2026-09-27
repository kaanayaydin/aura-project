import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../core/weather_condition_labels.dart';
import '../models/suggestion.dart';
import '../models/weather_snapshot.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../widgets/perfume_recommendation_card.dart';
import '../widgets/suggested_piece_card.dart';
import '../widgets/weather_mini_card.dart';

class SuggestScreen extends ConsumerStatefulWidget {
  const SuggestScreen({super.key});

  @override
  ConsumerState<SuggestScreen> createState() => _SuggestScreenState();
}

class _SuggestScreenState extends ConsumerState<SuggestScreen> {
  double _temperature = 18;
  double _humidity = 55;
  OccasionOption _occasion = OccasionOption.meeting;
  bool _autoWeather = true;
  bool _weatherLoading = false;
  WeatherSnapshot? _weather;

  /// Istanbul varsayilan (backend ile ayni); gercek GPS sonraki dilimde.
  static const double _defaultLat = 41.0082;
  static const double _defaultLon = 28.9784;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadWeather());
  }

  Future<void> _loadWeather() async {
    setState(() => _weatherLoading = true);
    try {
      final weather = await ref.read(apiServiceProvider).fetchWeather(
            lat: _defaultLat,
            lon: _defaultLon,
          );
      if (!mounted) return;
      setState(() {
        _weather = weather;
        if (_autoWeather) {
          _temperature = weather.temperatureCelsius.clamp(-5, 40);
          _humidity = weather.humidityPercent.clamp(0, 100);
        }
      });
    } catch (_) {
      // Widget "henuz yuklenmedi" gostermeye devam eder
    } finally {
      if (mounted) {
        setState(() => _weatherLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final suggestion = ref.watch(suggestionProvider);

    return Scaffold(
      backgroundColor: AuraColors.background,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(28, 20, 28, 40),
          children: [
            Text('Aura', style: AuraTypography.h3),
            const SizedBox(height: 6),
            Text(
              'Bağlam ve termodinamik öneri',
              style: AuraTypography.caption,
            ),
            const SizedBox(height: 28),
            WeatherMiniCard(
              weather: _weather,
              autoEnabled: _autoWeather,
              loading: _weatherLoading,
              onRefresh: _loadWeather,
              onToggle: (value) {
                setState(() {
                  _autoWeather = value;
                  if (value && _weather != null) {
                    _temperature = _weather!.temperatureCelsius.clamp(-5, 40);
                    _humidity = _weather!.humidityPercent.clamp(0, 100);
                  }
                });
              },
            ),
            const SizedBox(height: 32),
            SliderTheme(
              data: _sliderTheme,
              child: Column(
                children: [
                  _SectionLabel(
                    label: 'Sıcaklık',
                    value: '${_temperature.round()}°C',
                  ),
                  Slider(
                    value: _temperature,
                    min: -5,
                    max: 40,
                    divisions: 45,
                    label: '${_temperature.round()}°C',
                    onChanged: _autoWeather
                        ? null
                        : (value) => setState(() => _temperature = value),
                  ),
                  const SizedBox(height: 12),
                  _SectionLabel(
                    label: 'Nem',
                    value: '${_humidity.round()}%',
                  ),
                  Slider(
                    value: _humidity,
                    min: 0,
                    max: 100,
                    divisions: 20,
                    label: '${_humidity.round()}%',
                    onChanged: _autoWeather
                        ? null
                        : (value) => setState(() => _humidity = value),
                  ),
                ],
              ),
            ),
            if (_autoWeather)
              Padding(
                padding: const EdgeInsets.only(top: 6, bottom: 8),
                child: Text(
                  'Otomatik mod: kaydırıcılar kilitli; öneri hava servisini kullanır.',
                  style: AuraTypography.caption,
                ),
              ),
            const SizedBox(height: 16),
            Text('Etkinlik', style: AuraTypography.h3),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: OccasionOption.values.map((option) {
                final selected = option == _occasion;
                return ChoiceChip(
                  label: Text(option.label),
                  selected: selected,
                  showCheckmark: false,
                  onSelected: (_) => setState(() => _occasion = option),
                  selectedColor: AuraColors.primaryAction,
                  backgroundColor: AuraColors.surface,
                  side: BorderSide.none,
                  labelStyle: TextStyle(
                    color: selected ? AuraColors.surface : AuraColors.textSecondary,
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            Text(_occasion.subtitle, style: AuraTypography.caption),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: suggestion.isLoading ? null : _request,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AuraColors.primaryAction,
                  foregroundColor: AuraColors.surface,
                  disabledBackgroundColor:
                      AuraColors.primaryAction.withValues(alpha: 0.38),
                  disabledForegroundColor: AuraColors.surface.withValues(alpha: 0.7),
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  elevation: 0,
                ),
                child: suggestion.isLoading
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AuraColors.surface,
                        ),
                      )
                    : Text(
                        _autoWeather ? 'Havaya göre kombin öner' : 'Kombin öner',
                      ),
              ),
            ),
            const SizedBox(height: 36),
            suggestion.when(
              loading: () => const SizedBox.shrink(),
              error: (error, _) => _SuggestError(message: _friendly(error)),
              data: (data) {
                if (data == null) {
                  return const _IdleHint();
                }
                return _SuggestionResult(
                  data: data,
                  onFavorite: () => _saveFavorite(data),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _request() async {
    await ref.read(suggestionProvider.notifier).request(
          temperature: _temperature,
          humidity: _humidity,
          occasion: _occasion,
          useAutoWeather: _autoWeather,
          latitude: _autoWeather ? _defaultLat : null,
          longitude: _autoWeather ? _defaultLon : null,
        );
  }

  Future<void> _saveFavorite(SuggestionResponse data) async {
    try {
      await ref.read(authSessionProvider.notifier).ensure();
      await ref.read(apiServiceProvider).saveFavorite(data);
      await ref.read(favoritesProvider.notifier).refresh(silent: true);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Kombin favorilere eklendi.')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_friendly(error))),
      );
    }
  }

  String _friendly(Object error) {
    if (error is ApiException) return error.message;
    return 'Öneri alınamadı. Lütfen tekrar dene.';
  }
}

final SliderThemeData _sliderTheme = SliderThemeData(
  trackHeight: 2,
  activeTrackColor: AuraColors.primaryAction,
  inactiveTrackColor: AuraColors.primaryAction.withValues(alpha: 0.18),
  thumbColor: AuraColors.surfaceElevated,
  overlayColor: AuraColors.primaryAction.withValues(alpha: 0.12),
  thumbShape: const RoundSliderThumbShape(
    enabledThumbRadius: 12,
    elevation: 3,
    pressedElevation: 4,
  ),
  overlayShape: const RoundSliderOverlayShape(overlayRadius: 24),
  disabledActiveTrackColor: AuraColors.primaryAction.withValues(alpha: 0.28),
  disabledInactiveTrackColor: AuraColors.primaryAction.withValues(alpha: 0.12),
  disabledThumbColor: AuraColors.primaryAction.withValues(alpha: 0.35),
  valueIndicatorColor: AuraColors.primaryAction,
  valueIndicatorTextStyle: const TextStyle(color: AuraColors.surface),
);

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(label, style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600)),
        const Spacer(),
        Text(
          value,
          style: AuraTypography.body.copyWith(
            color: AuraColors.primaryAction,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _IdleHint extends StatelessWidget {
  const _IdleHint();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      child: Text(
        'Otomatik havayı kullan veya kaydırıcıları ayarla; Aura üst, alt, aksesuar ve koku önersin.',
        style: AuraTypography.bodySecondary,
      ),
    );
  }
}

class _SuggestError extends StatelessWidget {
  const _SuggestError({required this.message});

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

class _SuggestionResult extends StatelessWidget {
  const _SuggestionResult({
    required this.data,
    required this.onFavorite,
  });

  final SuggestionResponse data;
  final VoidCallback onFavorite;

  @override
  Widget build(BuildContext context) {
    final condition = data.context.weatherCondition;
    final source = data.context.weatherSource;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: AuraColors.surfaceElevated,
            borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
            boxShadow: AuraShadows.cardShadow,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(data.vibe, style: AuraTypography.h2),
              const SizedBox(height: 10),
              Text(data.summary, style: AuraTypography.body),
              const SizedBox(height: 16),
              Row(
                children: [
                  _MetaChip(
                    label: 'Eşleşme',
                    value: '${(data.matchScore * 100).round()}%',
                  ),
                  const SizedBox(width: 8),
                  _MetaChip(
                    label: 'Mevsim',
                    value: seasonDisplayLabel(data.context.seasonBand),
                  ),
                  const SizedBox(width: 8),
                  _MetaChip(
                    label: data.colorHarmony == null ? 'Bağlam' : 'Renk',
                    value: data.colorHarmony?.typeLabel ??
                        occasionDisplayLabel(data.context.occasion),
                  ),
                ],
              ),
              if (data.context.autoWeather) ...[
                const SizedBox(height: 12),
                Text(
                  'Hava: ${data.context.temperatureCelsius.round()}°C'
                  '${condition != null && condition.isNotEmpty ? ', ${weatherConditionDisplayLabel(condition)}' : ''}'
                  '${source != null && source.isNotEmpty ? ' · ${weatherSourceDisplayLabel(source)}' : ''}',
                  style: AuraTypography.caption,
                ),
              ],
              if (data.colorHarmony != null) ...[
                const SizedBox(height: 12),
                Text(data.colorHarmony!.explanation, style: AuraTypography.caption),
              ],
            ],
          ),
        ),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: onFavorite,
            icon: const Icon(Icons.favorite_border),
            label: const Text('Kombini favorilere ekle'),
            style: OutlinedButton.styleFrom(
              foregroundColor: AuraColors.textPrimary,
              backgroundColor: AuraColors.surfaceElevated,
              side: BorderSide(
                color: AuraColors.textSecondary.withValues(alpha: 0.35),
              ),
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
              ),
            ),
          ),
        ),
        const SizedBox(height: 24),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              SuggestedPieceCard(title: 'Üst', piece: data.top),
              const SizedBox(width: 16),
              SuggestedPieceCard(title: 'Alt', piece: data.bottom),
              const SizedBox(width: 16),
              SuggestedPieceCard(title: 'Aksesuar', piece: data.accessory),
            ],
          ),
        ),
        if (data.perfumeRecommendation != null) ...[
          const SizedBox(height: 24),
          PerfumeRecommendationCard(perfume: data.perfumeRecommendation!),
        ],
        if (data.notes.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text('Notlar', style: AuraTypography.h3),
          const SizedBox(height: 10),
          ...data.notes.map(
            (note) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '·  ',
                    style: AuraTypography.body.copyWith(color: AuraColors.primaryAction),
                  ),
                  Expanded(child: Text(note, style: AuraTypography.bodySecondary)),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
        decoration: BoxDecoration(
          color: AuraColors.surface,
          borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: AuraTypography.caption),
            const SizedBox(height: 2),
            Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AuraTypography.body.copyWith(
                fontWeight: FontWeight.w600,
                fontSize: 13,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
