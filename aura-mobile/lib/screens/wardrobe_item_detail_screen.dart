import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../models/vton_job.dart';
import '../models/wardrobe_item.dart';
import '../providers/providers.dart';
import '../services/api_service.dart';
import '../services/person_photo_service.dart';
import '../widgets/aura_image.dart';
import '../widgets/before_after_split_view.dart';
import '../widgets/lookbook_save_action.dart';
import '../widgets/vton_confirm_card.dart';
import '../widgets/vton_daily_limit_dialog.dart';
import '../widgets/vton_person_source_sheet.dart';

/// Gardırop parça detayı + Virtual Try-On (Before/After split).
class WardrobeItemDetailScreen extends ConsumerStatefulWidget {
  const WardrobeItemDetailScreen({super.key, required this.item});

  final WardrobeItem item;

  @override
  ConsumerState<WardrobeItemDetailScreen> createState() =>
      _WardrobeItemDetailScreenState();
}

class _WardrobeItemDetailScreenState
    extends ConsumerState<WardrobeItemDetailScreen> {
  static const _pollInterval = Duration(seconds: 2);

  PersonPhoto? _personPhoto;
  bool _picking = false;
  bool _running = false;
  bool _savingLookbook = false;
  String? _statusMessage;
  VtonJob? _job;
  String? _error;
  Timer? _pollTimer;

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _onSanalDene() async {
    if (_running || _picking) return;
    setState(() => _error = null);

    final source = await showPersonPhotoSourceSheet(context);
    if (source == null || !mounted) return;

    setState(() => _picking = true);
    try {
      final photo =
          await ref.read(personPhotoServiceProvider).pick(source);
      if (!mounted) return;
      if (photo == null) {
        setState(() => _picking = false);
        return;
      }
      setState(() {
        _picking = false;
        _personPhoto = photo;
        _job = null;
        _statusMessage = null;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _picking = false;
        _error = _friendly(error);
      });
    }
  }

  Future<void> _onTransform() async {
    final photo = _personPhoto;
    if (photo == null || _running) return;

    setState(() {
      _running = true;
      _error = null;
      _job = null;
      _statusMessage = 'Silüet hazırlanıyor...';
    });

    try {
      await ref.read(authSessionProvider.notifier).ensure(userId: widget.item.userId);
      final api = ref.read(apiServiceProvider);
      String? personUrl;
      try {
        personUrl = await api.uploadImageBytes(
          purpose: 'VTON_PERSON',
          bytes: photo.bytes,
          contentType: 'image/jpeg',
          filename: 'person',
        );
      } catch (_) {
        personUrl = null;
      }
      final created = await api.requestVton(
        wardrobeItemId: widget.item.id,
        personImageUrl: personUrl,
        personImageBase64: personUrl == null ? photo.base64 : null,
        userId: widget.item.userId,
      );
      if (!mounted) return;
      setState(() {
        _job = created;
        _statusMessage = _messageFor(created.status);
      });
      _beginPolling(created.jobId, userId: widget.item.userId);
    } catch (error) {
      if (!mounted) return;
      if (error is ApiException && error.statusCode == 429) {
        setState(() {
          _running = false;
          _statusMessage = null;
          _error = null;
        });
        await showVtonDailyLimitDialog(context);
        return;
      }
      setState(() {
        _running = false;
        _statusMessage = null;
        _error = _friendly(error);
      });
    }
  }

  void _clearPersonPhoto() {
    if (_running) return;
    setState(() {
      _personPhoto = null;
      _statusMessage = null;
      _error = null;
    });
  }

  void _beginPolling(int jobId, {int? userId}) {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(_pollInterval, (_) => _pollOnce(jobId, userId));
    unawaited(_pollOnce(jobId, userId));
  }

  Future<void> _pollOnce(int jobId, int? userId) async {
    try {
      final api = ref.read(apiServiceProvider);
      final job = await api.fetchVtonStatus(jobId, userId: userId);
      if (!mounted) return;
      setState(() {
        _job = job;
        _statusMessage = _messageFor(job.status);
        if (job.isTerminal) {
          _running = false;
          _pollTimer?.cancel();
          _pollTimer = null;
          if (job.status == 'FAILED') {
            _error = job.errorMessage ?? 'VTON basarisiz.';
          }
        }
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _running = false;
        _pollTimer?.cancel();
        _pollTimer = null;
        _error = _friendly(error);
      });
    }
  }

  Future<void> _onSaveLookbook() async {
    final job = _job;
    if (job == null || !job.isCompleted || job.lookbookSaved || _savingLookbook) {
      return;
    }
    setState(() => _savingLookbook = true);
    try {
      await ref.read(authSessionProvider.notifier).ensure(userId: job.userId);
      await ref.read(lookbookProvider.notifier).save(
            jobId: job.jobId,
            userId: job.userId,
          );
      if (!mounted) return;
      setState(() {
        _job = job.copyWith(lookbookSaved: true);
        _savingLookbook = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Lookbook'a eklendi.")),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _savingLookbook = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_friendly(error))),
      );
    }
  }

  String _messageFor(String status) {
    return switch (status) {
      'QUEUED' => 'Kuyrukta bekleniyor...',
      'PROCESSING' => 'Silüet hazırlanıyor...',
      'COMPLETED' => 'Deneme hazir.',
      'FAILED' => 'Islem basarisiz.',
      _ => 'Silüet hazırlanıyor...',
    };
  }

  String _friendly(Object error) {
    if (error is ApiException) return error.message;
    return error.toString();
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final resultBytes = _decode(_job?.resultImageBase64);
    final hasPreview = _personPhoto != null;

    return Scaffold(
      backgroundColor: AuraTheme.carbon,
      appBar: AppBar(
        title: Text(item.category),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                height: 320,
                  child: AuraImage(
                    bytes: item.decodedBytes,
                    imageUrl: item.displayUrl,
                    borderRadius: 18,
                  ),
              ),
              const SizedBox(height: 18),
              Text(
                item.category,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 6),
              Text(
                [
                  if (item.color != null && item.color!.isNotEmpty) item.color!,
                  'güven ${item.confidenceLabel}',
                ].join(' · '),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AuraTheme.mistMuted,
                    ),
              ),
              const SizedBox(height: 28),
              if (!hasPreview)
                ElevatedButton(
                  key: const Key('vton-try-on'),
                  onPressed: (_running || _picking) ? null : _onSanalDene,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AuraTheme.champagneGold,
                    foregroundColor: AuraTheme.carbon,
                    disabledBackgroundColor:
                        AuraTheme.champagneGold.withValues(alpha: 0.35),
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: _picking
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.2,
                            color: AuraTheme.carbon,
                          ),
                        )
                      : const Text('Sanal Dene'),
                ),
              if (hasPreview) ...[
                VtonConfirmCard(
                  personBytes: _personPhoto!.bytes,
                  garmentBytes: item.decodedBytes,
                  busy: _running,
                  onConfirm: _onTransform,
                  onCancel: _clearPersonPhoto,
                ),
              ],
              if (_running || _statusMessage != null) ...[
                const SizedBox(height: 28),
                if (_running)
                  const Center(
                    child: SizedBox(
                      width: 28,
                      height: 28,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.4,
                        color: AuraTheme.champagneGold,
                      ),
                    ),
                  ),
                if (_running) const SizedBox(height: 14),
                Text(
                  _statusMessage ?? '',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.champagneGold,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.2,
                      ),
                ),
              ],
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(
                  _error!,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: AuraTheme.danger,
                      ),
                ),
              ],
              if (_job?.isCompleted == true) ...[
                const SizedBox(height: 28),
                Text(
                  'Sonuç',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                if (_personPhoto != null)
                  BeforeAfterSplitView(
                    beforeBytes: _personPhoto!.bytes,
                    afterPreviewBytes: resultBytes,
                    afterImageUrl: _absoluteResultUrl(_job!),
                    afterImageHeaders: _resultHeaders(_job!),
                  )
                else
                  SizedBox(
                    height: 360,
                    child: AuraImage(bytes: resultBytes, borderRadius: 18),
                  ),
                const SizedBox(height: 8),
                LookbookSaveAction(
                  saved: _job!.lookbookSaved,
                  busy: _savingLookbook,
                  onPressed: _onSaveLookbook,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  String? _absoluteResultUrl(VtonJob job) {
    final path = job.resultImageUrl;
    if (path == null || path.isEmpty) return null;
    return ref.read(apiServiceProvider).absoluteVtonResultUrl(path);
  }

  Map<String, String> _resultHeaders(VtonJob job) =>
      ref.read(apiServiceProvider).vtonImageHeaders();

  Uint8List? _decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      return base64Decode(raw);
    } catch (_) {
      return null;
    }
  }
}
