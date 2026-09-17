import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
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
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
          children: [
            Text('Aura', style: Theme.of(context).textTheme.displayMedium),
            const SizedBox(height: 4),
            Text(
              'Baglam ve termodinamik oneri',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                  ),
            ),
            const SizedBox(height: 20),
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
            const SizedBox(height: 24),
            IgnorePointer(
              ignoring: _autoWeather,
              child: Opacity(
                opacity: _autoWeather ? 0.45 : 1,
                child: Column(
                  children: [
                    _SectionLabel(
                      label: 'Sicaklik',
                      value: '${_temperature.round()}°C',
                    ),
                    Slider(
                      value: _temperature,
                      min: -5,
                      max: 40,
                      divisions: 45,
                      label: '${_temperature.round()}°C',
                      onChanged: (value) => setState(() => _temperature = value),
                    ),
                    const SizedBox(height: 8),
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
                      onChanged: (value) => setState(() => _humidity = value),
                    ),
                  ],
                ),
              ),
            ),
            if (_autoWeather)
              Padding(
                padding: const EdgeInsets.only(top: 4, bottom: 8),
                child: Text(
                  'Otomatik mod: kaydiricilar kilitli; oneri hava servisini kullanir.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 11,
                      ),
                ),
              ),
            const SizedBox(height: 12),
            Text(
              'Etkinlik',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: OccasionOption.values.map((option) {
                final selected = option == _occasion;
                return ChoiceChip(
                  label: Text(option.label),
                  selected: selected,
                  onSelected: (_) => setState(() => _occasion = option),
                  selectedColor: AuraTheme.champagne.withValues(alpha: 0.25),
                  labelStyle: TextStyle(
                    color: selected ? AuraTheme.champagne : AuraTheme.mist,
                    fontWeight: FontWeight.w600,
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 8),
            Text(
              _occasion.subtitle,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AuraTheme.mistMuted,
                    fontSize: 12,
                  ),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: suggestion.isLoading ? null : _request,
                child: suggestion.isLoading
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(_autoWeather ? 'Havaya gore kombin oner' : 'Kombin oner'),
              ),
            ),
            const SizedBox(height: 28),
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
    return 'Oneri alinamadi. Backend 8080 portunda mi?';
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(label, style: Theme.of(context).textTheme.titleLarge),
        const Spacer(),
        Text(
          value,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                color: AuraTheme.champagne,
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
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AuraTheme.carbonSoft),
      ),
      child: Text(
        'Otomatik havayi kullan veya kaydiricilari ayarla; Aura ust / alt / aksesuar + koku onersin.',
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: AuraTheme.mistMuted,
            ),
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
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AuraTheme.danger.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(message),
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF252A30), Color(0xFF1A1D21)],
            ),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.25)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                data.vibe,
                style: Theme.of(context).textTheme.displayMedium?.copyWith(
                      fontSize: 26,
                      color: AuraTheme.champagne,
                    ),
              ),
              const SizedBox(height: 8),
              Text(data.summary),
              const SizedBox(height: 14),
              Row(
                children: [
                  _MetaChip(
                    label: 'Eslesme',
                    value: '${(data.matchScore * 100).round()}%',
                  ),
                  const SizedBox(width: 8),
                  _MetaChip(
                    label: 'Mevsim',
                    value: data.context.seasonBand,
                  ),
                  const SizedBox(width: 8),
                  _MetaChip(
                    label: data.colorHarmony == null ? 'Baglam' : 'Renk',
                    value: data.colorHarmony?.typeLabel ?? data.context.occasion,
                  ),
                ],
              ),
              if (data.context.autoWeather) ...[
                const SizedBox(height: 10),
                Text(
                  'Hava: ${data.context.temperatureCelsius.round()}°C'
                  '${data.context.weatherCondition != null ? ', ${data.context.weatherCondition}' : ''}'
                  '${data.context.weatherSource != null ? ' (${data.context.weatherSource})' : ''}',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 12,
                      ),
                ),
              ],
              if (data.colorHarmony != null) ...[
                const SizedBox(height: 10),
                Text(
                  data.colorHarmony!.explanation,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 12,
                      ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: onFavorite,
            icon: const Icon(Icons.favorite_border),
            label: const Text('Kombini Favorilere Ekle'),
          ),
        ),
        const SizedBox(height: 20),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              SuggestedPieceCard(title: 'Ust', piece: data.top),
              const SizedBox(width: 12),
              SuggestedPieceCard(title: 'Alt', piece: data.bottom),
              const SizedBox(width: 12),
              SuggestedPieceCard(title: 'Aksesuar', piece: data.accessory),
            ],
          ),
        ),
        if (data.perfumeRecommendation != null) ...[
          const SizedBox(height: 20),
          PerfumeRecommendationCard(perfume: data.perfumeRecommendation!),
        ],
        if (data.notes.isNotEmpty) ...[
          const SizedBox(height: 20),
          Text('Notlar', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          ...data.notes.map(
            (note) => Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('·  ', style: TextStyle(color: AuraTheme.champagne)),
                  Expanded(
                    child: Text(
                      note,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: AuraTheme.mistMuted,
                          ),
                    ),
                  ),
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
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: AuraTheme.carbon.withValues(alpha: 0.55),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label.toUpperCase(),
              style: const TextStyle(
                color: AuraTheme.mistMuted,
                fontSize: 9,
                letterSpacing: 0.8,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AuraTheme.mist,
                fontWeight: FontWeight.w700,
                fontSize: 12,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
