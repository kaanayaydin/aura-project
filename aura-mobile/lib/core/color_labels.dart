/// İngilizce renk adını Türkçe görüntüleme etiketine çevirir.
///
/// Java `AuraStylistPrompt.humanColor` tablosuna dayanır; `navy` burada laciverttir.
/// Yalnızca ekranda kullanılır. API'deki `color` değişmez.
String colorDisplayLabel(String color) {
  final key = color.trim().toLowerCase();
  if (key.isEmpty) return color;
  return switch (key) {
    'black' || 'siyah' => 'siyah',
    'white' || 'beyaz' => 'beyaz',
    'navy' || 'lacivert' => 'lacivert',
    'grey' || 'gray' || 'gri' => 'gri',
    'beige' || 'bej' => 'bej',
    'brown' || 'kahverengi' => 'kahverengi',
    'blue' || 'mavi' => 'mavi',
    'green' || 'yeşil' || 'yesil' => 'yeşil',
    'red' || 'kırmızı' || 'kirmizi' => 'kırmızı',
    'yellow' || 'sarı' || 'sari' => 'sarı',
    'pink' || 'pembe' => 'pembe',
    'purple' || 'mor' => 'mor',
    'orange' || 'turuncu' => 'turuncu',
    'olive' || 'zeytin' => 'zeytin yeşili',
    'cream' || 'krem' => 'krem',
    'champagne' => 'şampanya tonu',
    _ => key,
  };
}
