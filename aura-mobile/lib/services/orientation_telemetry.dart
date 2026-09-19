import 'package:flutter/foundation.dart';

/// Yerel event log. confirmation_shown / confirmation_choice için
/// backend endpoint yok — POST atılmaz (ayrı backend görevi).
class OrientationTelemetry {
  OrientationTelemetry._();

  static void confirmationShown({
    required String confidence,
    required String suggested,
    required String jobId,
  }) {
    debugPrint(
      '[Aura][orientation] confirmation_shown '
      'confidence=$confidence suggested=$suggested job_id=$jobId',
    );
  }

  static void confirmationChoice({
    required String confidence,
    required String suggested,
    required int confirmedRotationDeg,
    required bool acceptedSuggestion,
    required bool useOriginal,
    required bool manualAdjust,
    required String jobId,
  }) {
    debugPrint(
      '[Aura][orientation] confirmation_choice '
      'confidence=$confidence suggested=$suggested '
      'confirmed_rotation_deg=$confirmedRotationDeg '
      'accepted_suggestion=$acceptedSuggestion '
      'use_original=$useOriginal manual_adjust=$manualAdjust '
      'job_id=$jobId',
    );
  }
}
