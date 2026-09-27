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
