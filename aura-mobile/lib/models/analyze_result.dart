/// Vision `/api/v1/vision/analyze` ozet cevabi.
class AnalyzeResult {
  const AnalyzeResult({
    required this.message,
    required this.stagesCompleted,
    required this.detectedCategories,
    this.wardrobeSynced,
    this.wardrobeSyncQueued,
  });

  final String message;
  final List<String> stagesCompleted;
  final List<DetectedCategory> detectedCategories;
  final int? wardrobeSynced;
  final int? wardrobeSyncQueued;

  factory AnalyzeResult.fromJson(Map<String, dynamic> json) {
    final items = (json['detected_items'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(DetectedCategory.fromJson)
        .where((item) => item.category != null)
        .toList();

    return AnalyzeResult(
      message: json['message'] as String? ?? '',
      stagesCompleted: (json['stages_completed'] as List<dynamic>? ?? const [])
          .map((e) => e.toString())
          .toList(),
      detectedCategories: items,
      wardrobeSynced: (json['wardrobe_synced'] as num?)?.toInt(),
      wardrobeSyncQueued: (json['wardrobe_sync_queued'] as num?)?.toInt(),
    );
  }

  bool get syncScheduled =>
      (wardrobeSyncQueued ?? 0) > 0 ||
      stagesCompleted.contains('backend_sync_scheduled') ||
      stagesCompleted.contains('backend_sync');

  /// SAM/bbox kesimi olan kategoriler (mobil → backend yazimi icin).
  List<DetectedCategory> get withCutouts => detectedCategories
      .where(
        (item) =>
            item.cutoutImageBase64 != null &&
            item.cutoutImageBase64!.isNotEmpty,
      )
      .toList();
}

class DetectedCategory {
  const DetectedCategory({
    required this.label,
    this.category,
    this.categoryConfidence,
    this.cutoutImageBase64,
    this.segmentationSource,
  });

  final String label;
  final String? category;
  final double? categoryConfidence;
  final String? cutoutImageBase64;
  final String? segmentationSource;

  factory DetectedCategory.fromJson(Map<String, dynamic> json) {
    final segmentation = json['segmentation'];
    String? source;
    if (segmentation is Map<String, dynamic>) {
      source = segmentation['source'] as String?;
    }
    return DetectedCategory(
      label: json['label'] as String? ?? '',
      category: json['category'] as String?,
      categoryConfidence: (json['category_confidence'] as num?)?.toDouble(),
      cutoutImageBase64: json['cutout_image_base64'] as String?,
      segmentationSource: source,
    );
  }
}
