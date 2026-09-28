import 'package:flutter/material.dart';

/// Kart ve chip köşe yarıçapları. Sohbet, Dolap, Öneri, Arşiv, Raf ve Ayarlar bunları kullanır.
class AuraRadii {
  AuraRadii._();

  static const double cardRadius = 12;
  static const double pillRadius = 24;
}

/// Sıcak, düşük kontrastlı kart gölgesi. Saf siyah değil.
class AuraShadows {
  AuraShadows._();

  /// Alpha 0x14, Color.withOpacity(0.08) yuvarlamasiyla ayni (20/255).
  static const List<BoxShadow> cardShadow = [
    BoxShadow(
      color: Color(0x143D2B1F),
      blurRadius: 16,
      offset: Offset(0, 6),
    ),
  ];
}
