import 'package:flutter/material.dart';

import '../core/quiet_luxury/aura_colors.dart';
import '../core/quiet_luxury/aura_shape.dart';
import '../core/quiet_luxury/aura_typography.dart';
import '../models/suggestion.dart';

/// Kombin önerisinin altında gösterilen koku kartı.
class PerfumeRecommendationCard extends StatelessWidget {
  const PerfumeRecommendationCard({super.key, required this.perfume});

  final PerfumeRecommendation perfume;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AuraColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
        boxShadow: AuraShadows.cardShadow,
      ),
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: AuraColors.surface,
                  borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
                ),
                child: const Icon(
                  Icons.spa_outlined,
                  color: AuraColors.primaryAction,
                  size: 22,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Koku önerisi', style: AuraTypography.caption),
                    const SizedBox(height: 2),
                    Text(
                      perfume.displayTitle,
                      style: AuraTypography.h3.copyWith(fontSize: 18),
                    ),
                  ],
                ),
              ),
              _ScoreBadge(score: perfume.score),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            '${perfume.concentration}  ·  yayılım ${perfume.diffusion}',
            style: AuraTypography.caption,
          ),
          const SizedBox(height: 12),
          Text(perfume.blurb, style: AuraTypography.body),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: perfume.chords
                .map(
                  (chord) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                      color: AuraColors.surface,
                      borderRadius: BorderRadius.circular(AuraRadii.pillRadius),
                    ),
                    child: Text(chord, style: AuraTypography.caption),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 18),
          _NoteRow(label: 'Üst nota', notes: perfume.topNotes),
          const SizedBox(height: 8),
          _NoteRow(label: 'Kalp nota', notes: perfume.heartNotes),
          const SizedBox(height: 8),
          _NoteRow(label: 'Dip nota', notes: perfume.baseNotes),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AuraColors.surface,
              borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Termodinamik', style: AuraTypography.caption),
                const SizedBox(height: 4),
                Text(perfume.thermodynamicNote, style: AuraTypography.bodySecondary),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ScoreBadge extends StatelessWidget {
  const _ScoreBadge({required this.score});

  final double score;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AuraColors.surface,
        borderRadius: BorderRadius.circular(AuraRadii.cardRadius),
      ),
      child: Text(
        '${(score * 100).round()}%',
        style: AuraTypography.caption.copyWith(
          color: AuraColors.primaryAction,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _NoteRow extends StatelessWidget {
  const _NoteRow({required this.label, required this.notes});

  final String label;
  final List<String> notes;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 78,
          child: Text(label, style: AuraTypography.caption),
        ),
        Expanded(
          child: Text(
            notes.isEmpty ? '—' : notes.join(' · '),
            style: AuraTypography.body,
          ),
        ),
      ],
    );
  }
}
