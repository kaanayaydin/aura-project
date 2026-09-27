import 'dart:io';

import 'package:aura_mobile/core/quiet_luxury/aura_colors.dart';
import 'package:aura_mobile/core/quiet_luxury/aura_shape.dart';
import 'package:aura_mobile/core/quiet_luxury/quiet_luxury_nav_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('quiet luxury renk, radius, golge ve nav ikonlari tanimli', () {
    expect(AuraColors.background, const Color(0xFFF2ECDC));
    expect(AuraColors.surface, const Color(0xFFFAF6EC));
    expect(AuraColors.surfaceElevated, const Color(0xFFFFFFFF));
    expect(AuraColors.primaryAction, const Color(0xFF3D2B1F));
    expect(AuraColors.textPrimary, const Color(0xFF2B2118));
    expect(AuraColors.textSecondary, const Color(0xFF6B5D4C));
    expect(AuraColors.accentRare, const Color(0xFFD4A548));
    expect(AuraColors.headerGradientStart, const Color(0xFFC08A4E));
    expect(AuraColors.headerGradientEnd, const Color(0xFF8B5A2B));
    expect(AuraColors.error, const Color(0xFFC0392B));

    expect(AuraRadii.cardRadius, 12);
    expect(AuraRadii.pillRadius, 24);
    expect(AuraShadows.cardShadow.single.color, const Color(0x143D2B1F));

    expect(QuietLuxuryNavIcons.dolap, isNotNull);
    expect(QuietLuxuryNavIcons.oneri, isNotNull);
    expect(QuietLuxuryNavIcons.auraAi, isNotNull);
    expect(QuietLuxuryNavIcons.arsiv, isNotNull);
    expect(QuietLuxuryNavIcons.raf, isNotNull);
  });

  test('quiet luxury yalnizca chat ve dolap ekranlarinda kullanilir', () {
    const allowed = {
      'lib/screens/aura_chat_screen.dart',
      'lib/widgets/chat_bubble.dart',
      'lib/screens/wardrobe_screen.dart',
      'lib/widgets/wardrobe_tile.dart',
    };
    final offenders = <String>[];
    for (final entity in Directory('lib').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) continue;
      final normalized = entity.path.replaceAll('\\', '/');
      if (normalized.contains('/quiet_luxury/')) continue;
      final source = entity.readAsStringSync();
      if (!source.contains('quiet_luxury/')) continue;
      final permitted = allowed.any(normalized.endsWith);
      if (!permitted) offenders.add(normalized);
    }
    expect(offenders, isEmpty);
  });
}
