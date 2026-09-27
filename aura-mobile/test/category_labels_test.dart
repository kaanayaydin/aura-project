import 'package:aura_mobile/core/category_labels.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('categoryDisplayLabel Java humanCategory ile ayni Turkce etiketleri verir', () {
    expect(categoryDisplayLabel('t-shirt'), 'tişört');
    expect(categoryDisplayLabel('T-Shirt'), 'tişört');
    expect(categoryDisplayLabel('tee'), 'tişört');
    expect(categoryDisplayLabel('shirt'), 'gömlek');
    expect(categoryDisplayLabel('pants'), 'pantolon');
    expect(categoryDisplayLabel(' trousers '), 'pantolon');
    expect(categoryDisplayLabel('jacket'), 'ceket');
    expect(categoryDisplayLabel('dress'), 'elbise');
    expect(categoryDisplayLabel('sneakers'), 'spor ayakkabı');
    expect(categoryDisplayLabel('jeans'), 'jean');
    expect(categoryDisplayLabel(''), 'parça');
    expect(categoryDisplayLabel('perfume bottle'), 'perfume bottle');
  });
}
