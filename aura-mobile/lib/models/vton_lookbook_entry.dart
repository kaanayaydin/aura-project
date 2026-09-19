/// Backend `VtonLookbookEntryResponse` eslemesi.
class VtonLookbookEntry {
  const VtonLookbookEntry({
    required this.jobId,
    required this.userId,
    required this.wardrobeItemId,
    this.category,
    this.color,
    this.resultImageUrl,
    this.resultImageBase64,
    this.createdAt,
    this.lookbookSaved = true,
  });

  final int jobId;
  final int userId;
  final int wardrobeItemId;
  final String? category;
  final String? color;
  final String? resultImageUrl;
  final String? resultImageBase64;
  final DateTime? createdAt;
  final bool lookbookSaved;

  factory VtonLookbookEntry.fromJson(Map<String, dynamic> json) {
    return VtonLookbookEntry(
      jobId: (json['jobId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      wardrobeItemId: (json['wardrobeItemId'] as num).toInt(),
      category: json['category'] as String?,
      color: json['color'] as String?,
      resultImageUrl: json['resultImageUrl'] as String?,
      resultImageBase64: json['resultImageBase64'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
      lookbookSaved: json['lookbookSaved'] as bool? ?? true,
    );
  }
}
