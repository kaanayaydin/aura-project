/// `GET /api/v1/aura/favorites` kayit modeli.
class OutfitFavorite {
  const OutfitFavorite({
    required this.id,
    required this.userId,
    required this.vibe,
    required this.summary,
    required this.occasion,
    this.temperatureCelsius,
    this.seasonBand,
    required this.matchScore,
    this.colorHarmonyType,
    this.colorHarmonyScore,
    this.topItemId,
    this.bottomItemId,
    this.accessoryItemId,
    this.perfumeCatalogId,
    this.perfumeLabel,
    this.createdAt,
  });

  final int id;
  final int userId;
  final String vibe;
  final String summary;
  final String occasion;
  final double? temperatureCelsius;
  final String? seasonBand;
  final double matchScore;
  final String? colorHarmonyType;
  final double? colorHarmonyScore;
  final int? topItemId;
  final int? bottomItemId;
  final int? accessoryItemId;
  final String? perfumeCatalogId;
  final String? perfumeLabel;
  final DateTime? createdAt;

  factory OutfitFavorite.fromJson(Map<String, dynamic> json) {
    return OutfitFavorite(
      id: (json['id'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      vibe: json['vibe'] as String? ?? '',
      summary: json['summary'] as String? ?? '',
      occasion: json['occasion'] as String? ?? '',
      temperatureCelsius: (json['temperatureCelsius'] as num?)?.toDouble(),
      seasonBand: json['seasonBand'] as String?,
      matchScore: (json['matchScore'] as num?)?.toDouble() ?? 0,
      colorHarmonyType: json['colorHarmonyType'] as String?,
      colorHarmonyScore: (json['colorHarmonyScore'] as num?)?.toDouble(),
      topItemId: (json['topItemId'] as num?)?.toInt(),
      bottomItemId: (json['bottomItemId'] as num?)?.toInt(),
      accessoryItemId: (json['accessoryItemId'] as num?)?.toInt(),
      perfumeCatalogId: json['perfumeCatalogId'] as String?,
      perfumeLabel: json['perfumeLabel'] as String?,
      createdAt: _parseInstant(json['createdAt']),
    );
  }

  static DateTime? _parseInstant(dynamic raw) {
    if (raw is! String || raw.isEmpty) return null;
    return DateTime.tryParse(raw);
  }

  String get occasionLabel => switch (occasion) {
        'meeting' => 'Toplanti',
        'casual' => 'Gunluk',
        'sport' => 'Spor',
        _ => occasion,
      };

  String get colorHarmonyLabel => switch (colorHarmonyType) {
        'monochrome' => 'Monokrom',
        'analogous' => 'Uyumlu ton',
        'contrast' => 'Kontrast',
        'neutral' => 'Notr',
        null => '—',
        '' => '—',
        _ => colorHarmonyType!,
      };

  int get pieceCount =>
      [topItemId, bottomItemId, accessoryItemId].where((id) => id != null).length;
}
