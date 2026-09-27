import 'package:flutter/material.dart';

/// Quiet Luxury paleti. Aura AI sohbeti bunu kullanır; diğer ekranlar hâlâ [AuraTheme].
class AuraColors {
  AuraColors._();

  static const Color background = Color(0xFFF2ECDC);
  static const Color surface = Color(0xFFFAF6EC);
  static const Color surfaceElevated = Color(0xFFFFFFFF);
  static const Color primaryAction = Color(0xFF3D2B1F);
  static const Color textPrimary = Color(0xFF2B2118);
  static const Color textSecondary = Color(0xFF6B5D4C);

  /// Yalnızca puan / rating gibi nadir vurgular.
  static const Color accentRare = Color(0xFFD4A548);

  static const Color headerGradientStart = Color(0xFFC08A4E);
  static const Color headerGradientEnd = Color(0xFF8B5A2B);

  /// Palete uyan yumuşak kırmızı; saf parlak kırmızı değil.
  static const Color error = Color(0xFFC0392B);
}
