import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../core/theme.dart';

/// Lüks Before/After kaydırmalı karşılaştırma (şampanya sürgü).
///
/// [afterPreviewBytes] ile anında basılır; [afterImageUrl] yüklenince
/// yüksek çözünürlüklü hale geçilir.
class BeforeAfterSplitView extends StatefulWidget {
  const BeforeAfterSplitView({
    super.key,
    required this.beforeBytes,
    this.afterPreviewBytes,
    this.afterImageUrl,
    this.afterImageHeaders,
    this.height = 420,
  });

  final Uint8List beforeBytes;
  final Uint8List? afterPreviewBytes;
  final String? afterImageUrl;
  final Map<String, String>? afterImageHeaders;
  final double height;

  @override
  State<BeforeAfterSplitView> createState() => _BeforeAfterSplitViewState();
}

class _BeforeAfterSplitViewState extends State<BeforeAfterSplitView> {
  double _split = 0.5;
  ImageProvider? _afterHiRes;
  bool _hiResReady = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadHiRes());
  }

  @override
  void didUpdateWidget(covariant BeforeAfterSplitView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.afterImageUrl != widget.afterImageUrl) {
      _hiResReady = false;
      _afterHiRes = null;
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadHiRes());
    }
  }

  Future<void> _loadHiRes() async {
    final url = widget.afterImageUrl;
    if (url == null || url.isEmpty || !mounted) return;
    final provider = NetworkImage(url, headers: widget.afterImageHeaders);
    try {
      await precacheImage(provider, context);
      if (!mounted) return;
      setState(() {
        _afterHiRes = provider;
        _hiResReady = true;
      });
    } catch (_) {
      // Onizleme base64 kalir
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: widget.height,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final width = constraints.maxWidth;
              final splitX = width * _split;
              return GestureDetector(
                key: const Key('vton-before-after'),
                onHorizontalDragUpdate: (details) {
                  setState(() {
                    _split = (_split + details.delta.dx / width).clamp(0.05, 0.95);
                  });
                },
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(18),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      _AfterLayer(
                        previewBytes: widget.afterPreviewBytes,
                        hiRes: _hiResReady ? _afterHiRes : null,
                      ),
                      ClipRect(
                        clipper: _LeftClipper(_split),
                        child: Image.memory(
                          widget.beforeBytes,
                          fit: BoxFit.cover,
                          width: width,
                          height: widget.height,
                          gaplessPlayback: true,
                        ),
                      ),
                      Positioned(
                        left: splitX - 1,
                        top: 0,
                        bottom: 0,
                        child: Container(
                          width: 2,
                          color: AuraTheme.champagneGold,
                        ),
                      ),
                      Positioned(
                        left: splitX - 18,
                        top: widget.height / 2 - 18,
                        child: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: AuraTheme.champagneGold,
                            shape: BoxShape.circle,
                            border: Border.all(color: AuraTheme.carbon, width: 2),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.35),
                                blurRadius: 8,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.drag_handle_rounded,
                            color: AuraTheme.carbon,
                            size: 18,
                          ),
                        ),
                      ),
                      const Positioned(
                        left: 12,
                        top: 12,
                        child: _Badge(label: 'Önce'),
                      ),
                      const Positioned(
                        right: 12,
                        top: 12,
                        child: _Badge(label: 'Sonra'),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 10),
        Text(
          'Sürgüyü kaydırarak karşılaştır',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: AuraTheme.mistMuted,
                fontSize: 12,
              ),
        ),
      ],
    );
  }
}

class _AfterLayer extends StatelessWidget {
  const _AfterLayer({required this.previewBytes, required this.hiRes});

  final Uint8List? previewBytes;
  final ImageProvider? hiRes;

  @override
  Widget build(BuildContext context) {
    if (hiRes != null) {
      return Image(
        image: hiRes!,
        fit: BoxFit.cover,
        gaplessPlayback: true,
        errorBuilder: (_, __, ___) => _previewOrPlaceholder(),
      );
    }
    return _previewOrPlaceholder();
  }

  Widget _previewOrPlaceholder() {
    if (previewBytes != null) {
      return Image.memory(
        previewBytes!,
        fit: BoxFit.cover,
        gaplessPlayback: true,
      );
    }
    return Container(
      color: AuraTheme.carbonSoft,
      alignment: Alignment.center,
      child: const CircularProgressIndicator(
        strokeWidth: 2,
        color: AuraTheme.champagneGold,
      ),
    );
  }
}

class _LeftClipper extends CustomClipper<Rect> {
  _LeftClipper(this.fraction);

  final double fraction;

  @override
  Rect getClip(Size size) =>
      Rect.fromLTWH(0, 0, size.width * fraction, size.height);

  @override
  bool shouldReclip(covariant _LeftClipper oldClipper) =>
      oldClipper.fraction != fraction;
}

class _Badge extends StatelessWidget {
  const _Badge({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: AuraTheme.carbon.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AuraTheme.champagneGold.withValues(alpha: 0.45)),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: AuraTheme.champagneGold,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.4,
        ),
      ),
    );
  }
}
