import 'package:flutter/material.dart';

import '../core/theme.dart';
import '../models/wardrobe_item.dart';
import 'aura_image.dart';

class WardrobeTile extends StatelessWidget {
  const WardrobeTile({super.key, required this.item});

  final WardrobeItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AuraTheme.carbonSoft),
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
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.category,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontSize: 15,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  'guven ${item.confidenceLabel}',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 12,
                      ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
