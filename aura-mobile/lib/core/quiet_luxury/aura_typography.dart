import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'aura_colors.dart';

/// Başlık: Fraunces. Gövde ve UI: Manrope.
class AuraTypography {
  AuraTypography._();

  static TextStyle get h1 => GoogleFonts.fraunces(
        fontSize: 32,
        fontWeight: FontWeight.w600,
        height: 1.15,
        color: AuraColors.textPrimary,
      );

  static TextStyle get h2 => GoogleFonts.fraunces(
        fontSize: 24,
        fontWeight: FontWeight.w600,
        height: 1.2,
        color: AuraColors.textPrimary,
      );

  static TextStyle get h3 => GoogleFonts.fraunces(
        fontSize: 20,
        fontWeight: FontWeight.w500,
        height: 1.25,
        color: AuraColors.textPrimary,
      );

  static TextStyle get body => GoogleFonts.manrope(
        fontSize: 15,
        fontWeight: FontWeight.w400,
        height: 1.45,
        letterSpacing: 0.2,
        color: AuraColors.textPrimary,
      );

  static TextStyle get bodySecondary => GoogleFonts.manrope(
        fontSize: 15,
        fontWeight: FontWeight.w400,
        height: 1.45,
        letterSpacing: 0.2,
        color: AuraColors.textSecondary,
      );

  static TextStyle get caption => GoogleFonts.manrope(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        height: 1.3,
        letterSpacing: 0.3,
        color: AuraColors.textSecondary,
      );
}
