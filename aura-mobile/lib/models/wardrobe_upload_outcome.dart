import 'dart:typed_data';

import 'normalize_garment_result.dart';

/// Dolaba yükleme fazı: idle → loading → needsConfirmation | saving → idle.
sealed class WardrobeUploadPhase {
  const WardrobeUploadPhase();
}

final class WardrobeUploadIdle extends WardrobeUploadPhase {
  const WardrobeUploadIdle();
}

final class WardrobeUploadLoading extends WardrobeUploadPhase {
  const WardrobeUploadLoading();
}

/// Medium/low ensemble: onay ekranı bu durumda açılır.
final class PendingOrientationConfirmation extends WardrobeUploadPhase {
  const PendingOrientationConfirmation({
    required this.normalize,
    required this.category,
    this.categoryConfidence,
  });

  final NormalizeGarmentResult normalize;
  final String category;
  final double? categoryConfidence;
}

/// Onaylandı / high: backend cevabı beklenirken ızgarada placeholder.
final class WardrobeUploadSaving extends WardrobeUploadPhase {
  const WardrobeUploadSaving({
    required this.previewBytes,
    required this.category,
  });

  final Uint8List previewBytes;
  final String category;
}

final class WardrobeUploadError extends WardrobeUploadPhase {
  const WardrobeUploadError(this.message);
  final String message;
}

sealed class WardrobeUploadOutcome {
  const WardrobeUploadOutcome();
}

class WardrobeUploadDone extends WardrobeUploadOutcome {
  const WardrobeUploadDone(this.message);
  final String message;
}

class WardrobeUploadCancelled extends WardrobeUploadOutcome {
  const WardrobeUploadCancelled();
}

class WardrobeUploadNeedsConfirmation extends WardrobeUploadOutcome {
  const WardrobeUploadNeedsConfirmation(this.pending);
  final PendingOrientationConfirmation pending;
}
