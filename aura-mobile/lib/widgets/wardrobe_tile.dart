import 'package:flutter/material.dart';

import '../core/category_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/wardrobe_item.dart';
import 'aura_image.dart';

class WardrobeTile extends StatelessWidget {
  const WardrobeTile({super.key, required this.item});

  final WardrobeItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: AuraImage(
              bytes: item.decodedBytes,
              imageUrl: item.displayUrl,
              borderRadius: 0,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
            child: Text(
              categoryDisplayLabel(item.category),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}
