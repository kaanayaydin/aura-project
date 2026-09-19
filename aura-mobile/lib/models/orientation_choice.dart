/// Kardinal yön matematiği — backend CCW, Flutter RotatedBox CW.
///
/// Backend: top=0, right=90 CCW, bottom=180, left=270 CCW.
/// Flutter RotatedBox.quarterTurns: saat yönü çeyrek tur.
class OrientationChoice {
  const OrientationChoice._();

  static const edges = ['top', 'right', 'bottom', 'left'];

  /// Yakayı kuzeye getiren CCW derece.
  static int suggestedCcwDeg(String edge) {
    switch (edge.trim().toLowerCase()) {
      case 'right':
        return 90;
      case 'bottom':
        return 180;
      case 'left':
        return 270;
      default:
        return 0;
    }
  }

  /// Öneri için istemci önizleme (saat yönü çeyrek tur).
  static int suggestedCwQuarterTurns(String edge) {
    final ccw = suggestedCcwDeg(edge);
    if (ccw == 0) return 0;
    return (4 - (ccw ~/ 90)) % 4;
  }

  /// CW çeyrek tur → backend `confirmed_rotation_deg` (CCW).
  static int cwTurnsToCcwDeg(int quarterTurnsCw) {
    final t = quarterTurnsCw % 4;
    final normalized = t < 0 ? t + 4 : t;
    return ((4 - normalized) % 4) * 90;
  }

  static int addCwTurns(int current, int delta) => (current + delta) % 4;

  /// Kullanıcı kararı → backend'e gönderilecek CCW derece.
  /// [useOriginal] low kaçış yolu: 0, döndürme yok.
  static int confirmedRotationDeg({
    required bool useOriginal,
    required int previewCwTurns,
  }) {
    if (useOriginal) return 0;
    return cwTurnsToCcwDeg(previewCwTurns);
  }
}

/// Onay ekranından dönen karar.
class OrientationConfirmDecision {
  const OrientationConfirmDecision({
    required this.confirmedRotationDeg,
    required this.previewCwTurns,
    required this.acceptedSuggestion,
    required this.useOriginal,
    required this.manualAdjust,
  });

  /// Backend sözleşmesi: CCW 0/90/180/270.
  final int confirmedRotationDeg;
  final int previewCwTurns;
  final bool acceptedSuggestion;
  final bool useOriginal;
  final bool manualAdjust;
}
