/// Kullanici parfum rafi / katalog kaydi.
class UserPerfume {
  const UserPerfume({
    this.id,
    this.userId,
    required this.catalogId,
    required this.brand,
    required this.name,
    this.concentration,
    required this.chords,
    this.notes,
    this.diffusion,
    required this.topNotes,
    required this.heartNotes,
    required this.baseNotes,
    this.blurb,
    required this.onShelf,
  });

  final int? id;
  final int? userId;
  final String catalogId;
  final String brand;
  final String name;
  final String? concentration;
  final List<String> chords;
  final String? notes;
  final String? diffusion;
  final List<String> topNotes;
  final List<String> heartNotes;
  final List<String> baseNotes;
  final String? blurb;
  final bool onShelf;

  factory UserPerfume.fromJson(Map<String, dynamic> json) {
    List<String> list(String key) => (json[key] as List<dynamic>? ?? const [])
        .map((e) => e.toString())
        .toList();

    return UserPerfume(
      id: (json['id'] as num?)?.toInt(),
      userId: (json['userId'] as num?)?.toInt(),
      catalogId: json['catalogId'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
      name: json['name'] as String? ?? '',
      concentration: json['concentration'] as String?,
      chords: list('chords'),
      notes: json['notes'] as String?,
      diffusion: json['diffusion'] as String?,
      topNotes: list('topNotes'),
      heartNotes: list('heartNotes'),
      baseNotes: list('baseNotes'),
      blurb: json['blurb'] as String?,
      onShelf: json['onShelf'] as bool? ?? false,
    );
  }

  String get displayTitle => '$brand — $name';
}
