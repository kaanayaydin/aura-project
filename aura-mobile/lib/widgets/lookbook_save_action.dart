import 'package:flutter/material.dart';

import '../core/theme.dart';

/// Şampanya çizgili Lookbook kaydet aksiyonu.
class LookbookSaveAction extends StatelessWidget {
  const LookbookSaveAction({
    super.key,
    required this.saved,
    required this.busy,
    required this.onPressed,
  });

  final bool saved;
  final bool busy;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          height: 1,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AuraTheme.champagneGold.withValues(alpha: 0.0),
                AuraTheme.champagneGold,
                AuraTheme.champagneGold.withValues(alpha: 0.0),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        OutlinedButton(
          key: const Key('lookbook-save'),
          onPressed: (saved || busy) ? null : onPressed,
          style: OutlinedButton.styleFrom(
            foregroundColor: AuraTheme.champagneGold,
            disabledForegroundColor: AuraTheme.champagneGold.withValues(alpha: 0.7),
            side: BorderSide(
              color: AuraTheme.champagneGold.withValues(alpha: saved ? 0.45 : 0.9),
              width: 1.2,
            ),
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
          ),
          child: busy
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AuraTheme.champagneGold,
                  ),
                )
              : Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      saved ? Icons.check_circle_outline : Icons.auto_stories_outlined,
                      size: 18,
                      color: AuraTheme.champagneGold,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      saved ? 'Kaydedildi' : "Lookbook'a Ekle",
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.3,
                      ),
                    ),
                  ],
                ),
        ),
      ],
    );
  }
}
