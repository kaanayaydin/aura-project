import 'package:flutter/material.dart';

import '../core/theme.dart';
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
      width: 148,
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: piece == null
              ? AuraTheme.carbonSoft
              : AuraTheme.champagne.withValues(alpha: 0.35),
        ),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title.toUpperCase(),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: AuraTheme.champagne,
                  fontSize: 11,
                  letterSpacing: 1.2,
                ),
          ),
          const SizedBox(height: 10),
          AspectRatio(
            aspectRatio: 1,
            child: AuraImage(bytes: item?.decodedBytes, imageUrl: item?.displayUrl),
          ),
          const SizedBox(height: 10),
          Text(
            item?.category ?? 'Eksik',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 14),
          ),
          const SizedBox(height: 4),
          Text(
            piece == null
                ? 'Dolapta uygun parca yok'
                : 'skor ${(piece!.score * 100).round()}%',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                  fontSize: 11,
                ),
          ),
        ],
      ),
    );
  }
}
