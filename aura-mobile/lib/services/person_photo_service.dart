import 'dart:convert';
import 'dart:typed_data';

import 'package:image_picker/image_picker.dart';

/// Normalize edilmis kisi fotografi (VTON personImageBase64).
class PersonPhoto {
  const PersonPhoto({
    required this.bytes,
    required this.base64,
  });

  final Uint8List bytes;
  final String base64;

  factory PersonPhoto.fromBytes(Uint8List bytes) {
    return PersonPhoto(
      bytes: bytes,
      base64: base64Encode(bytes),
    );
  }
}

/// Kamera/galeri → boyutlandirilmis JPEG baytlari.
class PersonPhotoService {
  PersonPhotoService({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;

  /// maxWidth/quality ile normalize eder; iptalde null.
  Future<PersonPhoto?> pick(ImageSource source) async {
    final file = await _picker.pickImage(
      source: source,
      maxWidth: 1280,
      maxHeight: 1920,
      imageQuality: 85,
      preferredCameraDevice: CameraDevice.rear,
    );
    if (file == null) return null;
    final bytes = await file.readAsBytes();
    if (bytes.isEmpty) return null;
    return PersonPhoto.fromBytes(bytes);
  }
}
