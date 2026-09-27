/// İngilizce dolap kategorisini Türkçe görüntüleme etiketine çevirir.
///
/// Java `AuraStylistPrompt.humanCategory` ile aynı eşleme. Yalnızca ekranda
/// kullanılır; `item.category` ve API değeri değişmez.
String categoryDisplayLabel(String category) {
  final key = category.trim().toLowerCase().replaceAll('_', '-');
  if (key.isEmpty) return 'parça';
  return switch (key) {
    't-shirt' || 'tshirt' || 'tee' => 'tişört',
    'shirt' => 'gömlek',
    'blouse' => 'bluz',
    'sweater' || 'knit' => 'kazak',
    'hoodie' => 'hoodie',
    'jacket' => 'ceket',
    'coat' => 'palto',
    'blazer' => 'blazer',
    'pants' || 'trousers' => 'pantolon',
    'jeans' => 'jean',
    'shorts' => 'şort',
    'skirt' => 'etek',
    'dress' => 'elbise',
    'sneakers' => 'spor ayakkabı',
    'shoes' || 'loafers' => 'ayakkabı',
    'boots' => 'bot',
    'watch' => 'saat',
    'bag' || 'handbag' => 'çanta',
    'belt' => 'kemer',
    'scarf' => 'atkı',
    'hat' || 'cap' => 'şapka',
    'accessory' => 'aksesuar',
    _ => key,
  };
}
