import 'package:flutter/material.dart';

import '../core/theme.dart';
import '../models/normalize_garment_result.dart';
import '../models/orientation_choice.dart';
import '../services/orientation_telemetry.dart';

/// Medium/low ensemble sonrası kullanıcı onay ekranı.
class OrientationConfirmationScreen extends StatefulWidget {
  const OrientationConfirmationScreen({
    super.key,
    required this.result,
  });

  final NormalizeGarmentResult result;

  @override
  State<OrientationConfirmationScreen> createState() =>
      _OrientationConfirmationScreenState();
}

class _OrientationConfirmationScreenState
    extends State<OrientationConfirmationScreen> {
  static const Color _bg = Color(0xFF0F1115);
  static const Color _card = Color(0xFF161920);

  late int _previewCwTurns;
  late final int _suggestedCwTurns;
  bool _useOriginal = false;

  @override
  void initState() {
    super.initState();
    _suggestedCwTurns = OrientationChoice.suggestedCwQuarterTurns(
      widget.result.rotationSuggested,
    );
    _previewCwTurns = _suggestedCwTurns;
    OrientationTelemetry.confirmationShown(
      confidence: widget.result.ensembleNormalized.isEmpty
          ? 'low'
          : widget.result.ensembleNormalized,
      suggested: widget.result.rotationSuggested,
      jobId: widget.result.jobId,
    );
  }

  bool get _isLow => widget.result.isLow || widget.result.ensembleNormalized.isEmpty;

  void _setTurns(int turns, {bool original = false}) {
    setState(() {
      _useOriginal = original;
      _previewCwTurns = original ? 0 : turns % 4;
    });
  }

  OrientationConfirmDecision _decision({
    required bool acceptedSuggestion,
    required bool manualAdjust,
    required bool useOriginal,
  }) {
    return OrientationConfirmDecision(
      confirmedRotationDeg: OrientationChoice.confirmedRotationDeg(
        useOriginal: useOriginal,
        previewCwTurns: useOriginal ? 0 : _previewCwTurns,
      ),
      previewCwTurns: useOriginal ? 0 : _previewCwTurns,
      acceptedSuggestion: acceptedSuggestion,
      useOriginal: useOriginal,
      manualAdjust: manualAdjust,
    );
  }

  void _submit(OrientationConfirmDecision decision) {
    OrientationTelemetry.confirmationChoice(
      confidence: widget.result.ensembleNormalized.isEmpty
          ? 'low'
          : widget.result.ensembleNormalized,
      suggested: widget.result.rotationSuggested,
      confirmedRotationDeg: decision.confirmedRotationDeg,
      acceptedSuggestion: decision.acceptedSuggestion,
      useOriginal: decision.useOriginal,
      manualAdjust: decision.manualAdjust,
      jobId: widget.result.jobId,
    );
    Navigator.of(context).pop(decision);
  }

  @override
  Widget build(BuildContext context) {
    final title = _isLow
        ? 'Yönünü tam çözemedik, yardımcı olur musun?'
        : 'Kıyafeti böyle mi çevirelim?';
    final titleColor = _isLow ? AuraTheme.mist : AuraTheme.champagneGold;

    return Scaffold(
      key: const Key('orientation-confirmation-screen'),
      backgroundColor: _bg,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      title,
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                            color: titleColor,
                            fontSize: 22,
                            height: 1.25,
                          ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close_rounded),
                    color: AuraTheme.mistMuted,
                  ),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Center(
                  child: Container(
                    decoration: BoxDecoration(
                      color: _card,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(
                        color: AuraTheme.champagneGold.withValues(alpha: 0.22),
                      ),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: AspectRatio(
                      aspectRatio: 3 / 4,
                      child: RotatedBox(
                        quarterTurns: _previewCwTurns,
                        child: Image.memory(
                          widget.result.imageBytes,
                          fit: BoxFit.contain,
                          gaplessPlayback: true,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Wrap(
                alignment: WrapAlignment.center,
                spacing: 8,
                runSpacing: 8,
                children: [
                  _ChoiceChip(
                    key: const Key('orientation-confirm-yes'),
                    label: 'Evet, doğru',
                    selected: !_useOriginal && _previewCwTurns == _suggestedCwTurns,
                    onTap: () => _setTurns(_suggestedCwTurns),
                  ),
                  _ChoiceChip(
                    key: const Key('orientation-rotate-cw'),
                    label: '90° sağa',
                    selected: !_useOriginal &&
                        _previewCwTurns ==
                            OrientationChoice.addCwTurns(_suggestedCwTurns, 1),
                    onTap: () => _setTurns(
                      OrientationChoice.addCwTurns(_previewCwTurns, 1),
                    ),
                  ),
                  _ChoiceChip(
                    key: const Key('orientation-rotate-ccw'),
                    label: '90° sola',
                    selected: !_useOriginal &&
                        _previewCwTurns ==
                            OrientationChoice.addCwTurns(_suggestedCwTurns, 3),
                    onTap: () => _setTurns(
                      OrientationChoice.addCwTurns(_previewCwTurns, 3),
                    ),
                  ),
                  _ChoiceChip(
                    key: const Key('orientation-rotate-180'),
                    label: '180°',
                    selected: !_useOriginal &&
                        _previewCwTurns ==
                            OrientationChoice.addCwTurns(_suggestedCwTurns, 2),
                    onTap: () => _setTurns(
                      OrientationChoice.addCwTurns(_previewCwTurns, 2),
                    ),
                  ),
                  if (_isLow)
                    _ChoiceChip(
                      key: const Key('orientation-use-original'),
                      label: 'Emin değilim, orijinal görseli kullan',
                      selected: _useOriginal,
                      onTap: () => _setTurns(0, original: true),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
              child: SizedBox(
                height: 52,
                child: ElevatedButton(
                  key: const Key('orientation-submit'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AuraTheme.champagneGold,
                    foregroundColor: _bg,
                  ),
                  onPressed: () {
                    final accepted = !_useOriginal &&
                        _previewCwTurns == _suggestedCwTurns;
                    _submit(
                      _decision(
                        acceptedSuggestion: accepted,
                        manualAdjust: !_useOriginal && !accepted,
                        useOriginal: _useOriginal,
                      ),
                    );
                  },
                  child: const Text('Onayla'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ChoiceChip extends StatelessWidget {
  const _ChoiceChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onTap(),
      selectedColor: AuraTheme.champagneGold.withValues(alpha: 0.28),
      labelStyle: TextStyle(
        color: selected ? AuraTheme.champagneGold : AuraTheme.mist,
        fontWeight: FontWeight.w600,
        fontSize: 12,
      ),
    );
  }
}

/// High yanıtında onay ekranı açılmaz.
bool shouldShowOrientationConfirmation(NormalizeGarmentResult result) {
  return result.needsConfirmation;
}
