import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../core/theme.dart';

/// VTON kisi fotografi kaynagi: Kamera / Galeri.
Future<ImageSource?> showPersonPhotoSourceSheet(BuildContext context) {
  return showModalBottomSheet<ImageSource>(
    context: context,
    backgroundColor: AuraTheme.carbonElevated,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (context) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(8, 12, 8, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AuraTheme.carbonSoft,
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Silüetini seç',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 4),
              Text(
                'Sanal deneme için tam boy fotoğraf önerilir.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.mistMuted,
                    ),
              ),
              const SizedBox(height: 8),
              ListTile(
                key: const Key('vton-source-camera'),
                leading: const Icon(
                  Icons.photo_camera_outlined,
                  color: AuraTheme.champagneGold,
                ),
                title: const Text('Kamera ile Çek'),
                onTap: () => Navigator.pop(context, ImageSource.camera),
              ),
              ListTile(
                key: const Key('vton-source-gallery'),
                leading: const Icon(
                  Icons.photo_library_outlined,
                  color: AuraTheme.champagneGold,
                ),
                title: const Text('Galeriden Seç'),
                onTap: () => Navigator.pop(context, ImageSource.gallery),
              ),
            ],
          ),
        ),
      );
    },
  );
}
