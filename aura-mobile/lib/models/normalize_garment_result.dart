import 'dart:convert';
import 'dart:typed_data';

/// Vision `POST /api/v1/vision/normalize-garment` cevabi.
/// Eski backend alanlari eksikse kirilmaz (onay varsayilan false).
class NormalizeGarmentResult {
  const NormalizeGarmentResult({
    required this.imageBytes,
    this.jobId = '',
    this.ensembleConfidence = '',
    this.rotationSuggested = 'top',
    this.requiresConfirmation = false,
    this.rotationDegApplied = 0,
    this.rotationMethod = 'none',
    this.message = '',
    this.width,
    this.height,
  });

  final Uint8List imageBytes;
  final String jobId;
  final String ensembleConfidence;
  final String rotationSuggested;
  final bool requiresConfirmation;
  final int rotationDegApplied;
  final String rotationMethod;
  final String message;
  final int? width;
  final int? height;

  String get ensembleNormalized => ensembleConfidence.trim().toLowerCase();

  bool get isHigh => ensembleNormalized == 'high';

  bool get isMedium => ensembleNormalized == 'medium';

  bool get isLow => ensembleNormalized == 'low';

  /// medium/low veya requiresConfirmation=true → onay UI.
  bool get needsConfirmation {
    if (isHigh) return false;
    if (isMedium || isLow) return true;
    return requiresConfirmation;
  }

  factory NormalizeGarmentResult.fromJson(Map<String, dynamic> json) {
    final b64 = json['image_base64'] as String? ?? json['imageBase64'] as String?;
    if (b64 == null || b64.isEmpty) {
      throw const FormatException('Normalize cevabinda image_base64 yok');
    }
    return NormalizeGarmentResult(
      imageBytes: base64Decode(b64),
      jobId: _string(json, const ['job_id', 'jobId']),
      ensembleConfidence: _string(json, const [
        'ensemble_confidence',
        'ensembleConfidence',
      ]),
      rotationSuggested: _string(
            json,
            const ['rotation_suggested', 'rotationSuggested'],
          ).isEmpty
          ? 'top'
          : _string(json, const ['rotation_suggested', 'rotationSuggested']),
      requiresConfirmation: json['requires_confirmation'] as bool? ??
          json['requiresConfirmation'] as bool? ??
          false,
      rotationDegApplied: (json['rotation_deg_applied'] as num?)?.toInt() ??
          (json['rotationDegApplied'] as num?)?.toInt() ??
          0,
      rotationMethod: _string(json, const [
        'rotation_method',
        'rotationMethod',
      ]).isEmpty
          ? 'none'
          : _string(json, const ['rotation_method', 'rotationMethod']),
      message: json['message'] as String? ?? '',
      width: (json['width'] as num?)?.toInt(),
      height: (json['height'] as num?)?.toInt(),
    );
  }

  static String _string(Map<String, dynamic> json, List<String> keys) {
    for (final key in keys) {
      final value = json[key];
      if (value is String && value.isNotEmpty) return value;
    }
    return '';
  }
}
