import 'dart:typed_data';
import 'dart:ui' as ui;

/// Saat yönü çeyrek tur PNG döndürme (istemci önizleme onayı sonrası).
Future<Uint8List> rotatePngClockwise(Uint8List bytes, int quarterTurnsCw) async {
  final turns = quarterTurnsCw % 4;
  if (turns == 0) return bytes;

  final codec = await ui.instantiateImageCodec(bytes);
  final frame = await codec.getNextFrame();
  final src = frame.image;
  final swap = turns.isOdd;
  final outW = swap ? src.height : src.width;
  final outH = swap ? src.width : src.height;

  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  canvas.translate(outW / 2.0, outH / 2.0);
  canvas.rotate(turns * 1.5707963267948966);
  canvas.translate(-src.width / 2.0, -src.height / 2.0);
  canvas.drawImage(src, ui.Offset.zero, ui.Paint());
  final picture = recorder.endRecording();
  final out = await picture.toImage(outW, outH);
  final data = await out.toByteData(format: ui.ImageByteFormat.png);
  src.dispose();
  out.dispose();
  if (data == null) return bytes;
  return data.buffer.asUint8List();
}
