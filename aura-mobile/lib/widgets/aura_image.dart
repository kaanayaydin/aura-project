import 'dart:developer' as developer;
import 'dart:typed_data';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../core/theme.dart';

/// Object storage URL veya bellek baytlari ile kiyafet gorseli.
class AuraImage extends StatelessWidget {
  const AuraImage({
    super.key,
    this.bytes,
    this.imageUrl,
    this.fit = BoxFit.cover,
    this.borderRadius = 16,
  });

  final Uint8List? bytes;
  final String? imageUrl;
  final BoxFit fit;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: _buildChild(),
    );
  }

  Widget _buildChild() {
    final url = imageUrl;
    if (url != null && url.isNotEmpty) {
      return CachedNetworkImage(
        imageUrl: url,
        fit: fit,
        placeholder: (_, __) => _placeholder(),
        errorWidget: (_, failedUrl, error) {
          developer.log(
            'AuraImage yuklenemedi url=$failedUrl error=$error',
            name: 'aura.image',
          );
          // ignore: avoid_print
          print('[Aura] image load fail url=$failedUrl err=$error');
          return _broken();
        },
      );
    }
    if (bytes != null) {
      return Image.memory(
        bytes!,
        fit: fit,
        gaplessPlayback: true,
        errorBuilder: (_, error, stackTrace) {
          developer.log(
            'AuraImage memory decode fail error=$error',
            name: 'aura.image',
          );
          return _broken();
        },
      );
    }
    return _placeholder();
  }

  Widget _placeholder() => Container(
        color: AuraTheme.carbonSoft,
        alignment: Alignment.center,
        child: const Icon(
          Icons.checkroom_outlined,
          color: AuraTheme.mistMuted,
          size: 36,
        ),
      );

  Widget _broken() => Container(
        color: AuraTheme.carbonSoft,
        alignment: Alignment.center,
        child: const Icon(Icons.broken_image_outlined, color: AuraTheme.mistMuted),
      );
}
