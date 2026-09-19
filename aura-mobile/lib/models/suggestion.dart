import 'wardrobe_item.dart';

/// `POST /api/v1/aura/suggest` cevabi.
class SuggestionResponse {
  const SuggestionResponse({
    required this.userId,
    required this.vibe,
    required this.summary,
    required this.context,
    this.top,
    this.bottom,
    this.accessory,
    this.perfumeRecommendation,
    this.colorHarmony,
    required this.matchScore,
    required this.notes,
  });

  final int userId;
  final String vibe;
  final String summary;
  final SuggestionContext context;
  final SuggestedPiece? top;
  final SuggestedPiece? bottom;
  final SuggestedPiece? accessory;
  final PerfumeRecommendation? perfumeRecommendation;
  final ColorHarmonyInfo? colorHarmony;
  final double matchScore;
  final List<String> notes;

  factory SuggestionResponse.fromJson(Map<String, dynamic> json) {
    final perfumeRaw = json['perfumeRecommendation'];
    final harmonyRaw = json['colorHarmony'];
    return SuggestionResponse(
      userId: (json['userId'] as num).toInt(),
      vibe: json['vibe'] as String? ?? '',
      summary: json['summary'] as String? ?? '',
      context: SuggestionContext.fromJson(
        json['context'] as Map<String, dynamic>? ?? const {},
      ),
      top: _piece(json['top']),
      bottom: _piece(json['bottom']),
      accessory: _piece(json['accessory']),
      perfumeRecommendation: perfumeRaw is Map<String, dynamic>
          ? PerfumeRecommendation.fromJson(perfumeRaw)
          : null,
      colorHarmony: harmonyRaw is Map<String, dynamic>
          ? ColorHarmonyInfo.fromJson(harmonyRaw)
          : null,
      matchScore: (json['matchScore'] as num?)?.toDouble() ?? 0,
      notes: (json['notes'] as List<dynamic>? ?? const [])
          .map((e) => e.toString())
          .toList(),
    );
  }

  static SuggestedPiece? _piece(dynamic raw) {
    if (raw is! Map<String, dynamic>) return null;
    return SuggestedPiece.fromJson(raw);
  }

  int get filledSlots {
    return [top, bottom, accessory].where((piece) => piece != null).length;
  }
}

class SuggestionContext {
  const SuggestionContext({
    required this.temperatureCelsius,
    this.humidityPercent,
    required this.occasion,
    required this.seasonBand,
    this.weatherSource,
    this.weatherCondition,
    this.weatherLocation,
    this.autoWeather = false,
  });

  final double temperatureCelsius;
  final double? humidityPercent;
  final String occasion;
  final String seasonBand;
  final String? weatherSource;
  final String? weatherCondition;
  final String? weatherLocation;
  final bool autoWeather;

  factory SuggestionContext.fromJson(Map<String, dynamic> json) {
    return SuggestionContext(
      temperatureCelsius: (json['temperatureCelsius'] as num?)?.toDouble() ?? 0,
      humidityPercent: (json['humidityPercent'] as num?)?.toDouble(),
      occasion: json['occasion'] as String? ?? '',
      seasonBand: json['seasonBand'] as String? ?? '',
      weatherSource: json['weatherSource'] as String?,
      weatherCondition: json['weatherCondition'] as String?,
      weatherLocation: json['weatherLocation'] as String?,
      autoWeather: json['autoWeather'] as bool? ?? false,
    );
  }
}

class SuggestedPiece {
  const SuggestedPiece({
    required this.role,
    required this.item,
    required this.score,
    required this.reason,
  });

  final String role;
  final WardrobeItem item;
  final double score;
  final String reason;

  factory SuggestedPiece.fromJson(Map<String, dynamic> json) {
    return SuggestedPiece(
      role: json['role'] as String? ?? '',
      item: WardrobeItem.fromJson(json['item'] as Map<String, dynamic>),
      score: (json['score'] as num?)?.toDouble() ?? 0,
      reason: json['reason'] as String? ?? '',
    );
  }
}

class ColorHarmonyInfo {
  const ColorHarmonyInfo({
    required this.type,
    required this.score,
    required this.explanation,
  });

  final String type;
  final double score;
  final String explanation;

  factory ColorHarmonyInfo.fromJson(Map<String, dynamic> json) {
    return ColorHarmonyInfo(
      type: json['type'] as String? ?? 'unknown',
      score: (json['score'] as num?)?.toDouble() ?? 0,
      explanation: json['explanation'] as String? ?? '',
    );
  }

  String get typeLabel => switch (type) {
        'monochrome' => 'Monokrom',
        'analogous' => 'Uyumlu ton',
        'contrast' => 'Kontrast',
        'neutral' => 'Notr',
        _ => type,
      };
}

class PerfumeRecommendation {
  const PerfumeRecommendation({
    required this.id,
    required this.brand,
    required this.name,
    required this.concentration,
    required this.chords,
    required this.topNotes,
    required this.heartNotes,
    required this.baseNotes,
    required this.diffusion,
    required this.blurb,
    required this.score,
    required this.reason,
    required this.thermodynamicNote,
  });

  final String id;
  final String brand;
  final String name;
  final String concentration;
  final List<String> chords;
  final List<String> topNotes;
  final List<String> heartNotes;
  final List<String> baseNotes;
  final String diffusion;
  final String blurb;
  final double score;
  final String reason;
  final String thermodynamicNote;

  factory PerfumeRecommendation.fromJson(Map<String, dynamic> json) {
    List<String> list(String key) => (json[key] as List<dynamic>? ?? const [])
        .map((e) => e.toString())
        .toList();

    return PerfumeRecommendation(
      id: json['id'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
      name: json['name'] as String? ?? '',
      concentration: json['concentration'] as String? ?? '',
      chords: list('chords'),
      topNotes: list('topNotes'),
      heartNotes: list('heartNotes'),
      baseNotes: list('baseNotes'),
      diffusion: json['diffusion'] as String? ?? '',
      blurb: json['blurb'] as String? ?? '',
      score: (json['score'] as num?)?.toDouble() ?? 0,
      reason: json['reason'] as String? ?? '',
      thermodynamicNote: json['thermodynamicNote'] as String? ?? '',
    );
  }

  String get displayTitle => '$brand — $name';
}

enum OccasionOption {
  meeting('meeting', 'Toplanti', 'Resmi / ofis'),
  casual('casual', 'Gunluk', 'Rahat / sehir'),
  sport('sport', 'Spor', 'Aktif / antrenman');

  const OccasionOption(this.apiValue, this.label, this.subtitle);

  final String apiValue;
  final String label;
  final String subtitle;
}
