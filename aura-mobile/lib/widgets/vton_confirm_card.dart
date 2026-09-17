import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../core/theme.dart';
import 'aura_image.dart';

/// Kişi + kıyafet yan yana önizleme; "Dönüştür" ile VTON başlar.
class VtonConfirmCard extends StatelessWidget {
  const VtonConfirmCard({
    super.key,
    required this.personBytes,
    required this.garmentBytes,
    required this.onConfirm,
    required this.onCancel,
    this.busy = false,
  });

  final Uint8List personBytes;
  final Uint8List? garmentBytes;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('vton-confirm-card'),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AuraTheme.carbonElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AuraTheme.champagneGold.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Onayla',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 4),
          Text(
            'Silüetin ve parça yan yana. Hazırsan dönüştür.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: _PreviewPane(
                  label: 'Sen',
                  bytes: personBytes,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _PreviewPane(
                  label: 'Parça',
                  bytes: garmentBytes,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: busy ? null : onCancel,
                  child: const Text('Vazgeç'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(
                  key: const Key('vton-transform'),
                  onPressed: busy ? null : onConfirm,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AuraTheme.champagneGold,
                    foregroundColor: AuraTheme.carbon,
                    disabledBackgroundColor:
                        AuraTheme.champagneGold.withValues(alpha: 0.35),
                  ),
                  child: busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AuraTheme.carbon,
                          ),
                        )
                      : const Text('Dönüştür'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PreviewPane extends StatelessWidget {
  const _PreviewPane({required this.label, required this.bytes});

  final String label;
  final Uint8List? bytes;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: AuraTheme.mistMuted,
              ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 180,
          child: AuraImage(bytes: bytes, borderRadius: 14),
        ),
      ],
    );
  }
}
