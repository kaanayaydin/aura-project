/// İngilizce koku akorunu Türkçe görüntüleme etiketine çevirir.
///
/// Yalnızca ekranda kullanılır. API'deki `chords` değeri değişmez.
String accordDisplayLabel(String accord) {
  final key = accord.trim().toLowerCase();
  if (key.isEmpty) return accord;
  return switch (key) {
    'citrus' => 'narenciye',
    'fresh' => 'ferah',
    'aromatic' => 'aromatik',
    'green' => 'yeşil',
    'aquatic' => 'sucul',
    'woody' => 'odunsu',
    'spicy' => 'baharatlı',
    'oriental' => 'oryantal',
    _ => key,
  };
}

/// Yayılım değerini Türkçe görüntüleme etiketine çevirir.
///
/// Yalnızca ekranda kullanılır. API'deki `diffusion` değişmez.
String diffusionDisplayLabel(String diffusion) {
  final key = diffusion.trim().toLowerCase();
  if (key.isEmpty) return diffusion;
  return switch (key) {
    'light' => 'hafif',
    'soft' => 'yumuşak',
    'moderate' => 'orta',
    'strong' => 'güçlü',
    _ => key,
  };
}
