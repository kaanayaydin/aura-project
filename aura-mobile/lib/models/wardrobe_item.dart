import 'dart:convert';
import 'dart:typed_data';

/// Backend `WardrobeItemResponse` eslemesi.
class WardrobeItem {
  const WardrobeItem({
    required this.id,
    required this.userId,
    required this.category,
    this.categoryConfidence,
    this.color,
    required this.imageBytes,
    this.imageMimeType,
    this.imageUrl,
    this.originalImageUrl,
    this.imageBase64,
    this.imageDataUri,
  });

  final int id;
  final int userId;
  final String category;
  final double? categoryConfidence;
  final String? color;
  final int imageBytes;
  final String? imageMimeType;
  final String? imageUrl;
  final String? originalImageUrl;
  final String? imageBase64;
  final String? imageDataUri;

  factory WardrobeItem.fromJson(Map<String, dynamic> json) {
    return WardrobeItem(
      id: (json['id'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      category: json['category'] as String? ?? 'unknown',
      categoryConfidence: (json['categoryConfidence'] as num?)?.toDouble(),
      color: json['color'] as String?,
      imageBytes: (json['imageBytes'] as num?)?.toInt() ?? 0,
      imageMimeType: json['imageMimeType'] as String?,
      imageUrl: json['imageUrl'] as String?,
      originalImageUrl: json['originalImageUrl'] as String?,
      imageBase64: json['imageBase64'] as String?,
      imageDataUri: json['imageDataUri'] as String?,
    );
  }

  /// Flutter `Image.memory` icin cozulmus baytlar; yoksa null.
  Uint8List? get decodedBytes {
    final raw = imageBase64;
    if (raw == null || raw.isEmpty) return null;
    try {
      return base64Decode(raw);
    } catch (_) {
      return null;
    }
  }

  /// Stüdyo normalize CDN / MinIO URL (katalog gorunumu).
  String? get displayUrl {
    final url = imageUrl;
    if (url != null && url.isNotEmpty) return url;
    return null;
  }

  String get confidenceLabel {
    final value = categoryConfidence;
    if (value == null) return '—';
    return '${(value * 100).round()}%';
  }
}
