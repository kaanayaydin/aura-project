import 'package:aura_mobile/core/accord_labels.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('accordDisplayLabel katalog akorlarini Turkceye cevirir', () {
    expect(accordDisplayLabel('citrus'), 'narenciye');
    expect(accordDisplayLabel('Fresh'), 'ferah');
    expect(accordDisplayLabel(' aromatic '), 'aromatik');
    expect(accordDisplayLabel('green'), 'yeşil');
    expect(accordDisplayLabel('aquatic'), 'sucul');
    expect(accordDisplayLabel('woody'), 'odunsu');
    expect(accordDisplayLabel('spicy'), 'baharatlı');
    expect(accordDisplayLabel('oriental'), 'oryantal');
    expect(accordDisplayLabel(''), '');
    expect(accordDisplayLabel('amber'), 'amber');
  });
}
