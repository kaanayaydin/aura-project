import 'package:flutter/material.dart';

import '../core/theme.dart';
import '../models/suggestion.dart';

/// Kombin onerisinin altinda gosterilen niche koku karti.
class PerfumeRecommendationCard extends StatelessWidget {
  const PerfumeRecommendationCard({super.key, required this.perfume});

  final PerfumeRecommendation perfume;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF2A2620), Color(0xFF1A1C1E)],
        ),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AuraTheme.champagne.withValues(alpha: 0.28)),
      ),
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: AuraTheme.champagne.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.spa_outlined,
                  color: AuraTheme.champagne,
                  size: 22,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'KOKU / PARFUM ONERISI',
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                            color: AuraTheme.champagne,
                            fontSize: 11,
                            letterSpacing: 1.3,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      perfume.displayTitle,
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                            fontSize: 18,
                          ),
                    ),
                  ],
                ),
              ),
              _ScoreBadge(score: perfume.score),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '${perfume.concentration}  ·  yayilim ${perfume.diffusion}',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                  fontSize: 12,
                ),
          ),
          const SizedBox(height: 12),
          Text(
            perfume.blurb,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.4),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: perfume.chords
                .map(
                  (chord) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                      color: AuraTheme.carbonSoft,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      chord,
                      style: const TextStyle(
                        color: AuraTheme.champagne,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 18),
          _NoteRow(label: 'Ust nota', notes: perfume.topNotes),
          const SizedBox(height: 8),
          _NoteRow(label: 'Kalp nota', notes: perfume.heartNotes),
          const SizedBox(height: 8),
          _NoteRow(label: 'Dip nota', notes: perfume.baseNotes),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AuraTheme.carbon.withValues(alpha: 0.45),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Termodinamik',
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: AuraTheme.mistMuted,
                        fontSize: 11,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  perfume.thermodynamicNote,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontSize: 12,
                        color: AuraTheme.mist,
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

class _ScoreBadge extends StatelessWidget {
  const _ScoreBadge({required this.score});

  final double score;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AuraTheme.champagne.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        '${(score * 100).round()}%',
        style: const TextStyle(
          color: AuraTheme.champagne,
          fontWeight: FontWeight.w800,
          fontSize: 13,
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
          width: 72,
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AuraTheme.mistMuted,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
          ),
        ),
        Expanded(
          child: Text(
            notes.isEmpty ? '—' : notes.join(' · '),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontSize: 13),
          ),
        ),
      ],
    );
  }
}
