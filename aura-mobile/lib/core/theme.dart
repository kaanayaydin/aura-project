import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Aura gorsel dili: koyu karbon zemin + sampanya vurgu.
/// Mor/indigo ve krem-terracotta klişelerinden bilincli olarak uzak.
class AuraTheme {
  AuraTheme._();

  static const Color carbon = Color(0xFF141618);
  static const Color carbonElevated = Color(0xFF1E2226);
  static const Color carbonSoft = Color(0xFF2A3036);
  static const Color champagne = Color(0xFFC9B896);
  static const Color champagneDeep = Color(0xFFA89068);
  /// VTON / lüks CTA vurgusu (klasik şampanya altın).
  static const Color champagneGold = Color(0xFFD4AF37);
  static const Color mist = Color(0xFFE8E4DC);
  static const Color mistMuted = Color(0xFF9A958C);
  static const Color danger = Color(0xFFC45C5C);

  static ThemeData get dark {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: carbon,
      colorScheme: const ColorScheme.dark(
        primary: champagne,
        onPrimary: carbon,
        secondary: champagneDeep,
        surface: carbonElevated,
        onSurface: mist,
        error: danger,
      ),
    );

    return base.copyWith(
      textTheme: GoogleFonts.manropeTextTheme(base.textTheme).apply(
        bodyColor: mist,
        displayColor: mist,
      ).copyWith(
        displayLarge: GoogleFonts.syne(
          fontSize: 40,
          fontWeight: FontWeight.w700,
          color: mist,
          letterSpacing: -1.2,
        ),
        displayMedium: GoogleFonts.syne(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          color: mist,
          letterSpacing: -0.8,
        ),
        headlineMedium: GoogleFonts.syne(
          fontSize: 22,
          fontWeight: FontWeight.w600,
          color: mist,
        ),
        titleLarge: GoogleFonts.manrope(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: mist,
        ),
        bodyMedium: GoogleFonts.manrope(
          fontSize: 14,
          height: 1.45,
          color: mist,
        ),
        labelLarge: GoogleFonts.manrope(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.4,
          color: mist,
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: GoogleFonts.syne(
          fontSize: 24,
          fontWeight: FontWeight.w700,
          color: mist,
        ),
        iconTheme: const IconThemeData(color: mist),
      ),
      cardTheme: CardThemeData(
        color: carbonElevated,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        margin: EdgeInsets.zero,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: carbonSoft,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        hintStyle: const TextStyle(color: mistMuted),
        labelStyle: const TextStyle(color: mistMuted),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: champagne,
          foregroundColor: carbon,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          textStyle: GoogleFonts.manrope(fontWeight: FontWeight.w700, fontSize: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: mist,
          side: const BorderSide(color: carbonSoft),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: carbonSoft,
        selectedColor: champagne.withValues(alpha: 0.22),
        labelStyle: GoogleFonts.manrope(color: mist, fontWeight: FontWeight.w600),
        secondaryLabelStyle: GoogleFonts.manrope(color: carbon),
        side: BorderSide.none,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: carbonElevated,
        indicatorColor: champagne.withValues(alpha: 0.18),
        labelTextStyle: WidgetStatePropertyAll(
          GoogleFonts.manrope(fontSize: 12, fontWeight: FontWeight.w600),
        ),
        iconTheme: const WidgetStatePropertyAll(IconThemeData(color: mistMuted)),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: champagne,
        inactiveTrackColor: carbonSoft,
        thumbColor: champagne,
        overlayColor: champagne.withValues(alpha: 0.15),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: carbonSoft,
        contentTextStyle: GoogleFonts.manrope(color: mist),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: champagne,
        foregroundColor: carbon,
        elevation: 0,
      ),
      dividerColor: carbonSoft,
    );
  }
}
