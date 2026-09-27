import 'package:flutter/material.dart';

import '../core/category_labels.dart';
import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/suggestion.dart';
import 'aura_image.dart';

class SuggestedPieceCard extends StatelessWidget {
  const SuggestedPieceCard({super.key, required this.piece, required this.title});

  final SuggestedPiece? piece;
  final String title;

  @override
  Widget build(BuildContext context) {
    final item = piece?.item;
    return Container(
      width: 156,
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AuraTypography.caption),
          const SizedBox(height: 12),
          AspectRatio(
            aspectRatio: 1,
            child: AuraImage(bytes: item?.decodedBytes, imageUrl: item?.displayUrl),
          ),
          const SizedBox(height: 12),
          Text(
            item == null ? 'Eksik' : categoryDisplayLabel(item.category),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: AuraTypography.body.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 4),
          Text(
            piece == null
                ? 'Dolapta uygun parça yok'
                : 'skor ${(piece!.score * 100).round()}%',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: AuraTypography.caption,
          ),
        ],
      ),
    );
  }
}
