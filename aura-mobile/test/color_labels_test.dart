import 'package:aura_mobile/core/color_labels.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('colorDisplayLabel renk adlarini Turkceye cevirir', () {
    expect(colorDisplayLabel('black'), 'siyah');
    expect(colorDisplayLabel('White'), 'beyaz');
    expect(colorDisplayLabel('navy'), 'lacivert');
    expect(colorDisplayLabel('olive'), 'zeytin yeşili');
    expect(colorDisplayLabel('gray'), 'gri');
    expect(colorDisplayLabel('grey'), 'gri');
    expect(colorDisplayLabel('beige'), 'bej');
    expect(colorDisplayLabel('brown'), 'kahverengi');
    expect(colorDisplayLabel('red'), 'kırmızı');
    expect(colorDisplayLabel('blue'), 'mavi');
    expect(colorDisplayLabel('green'), 'yeşil');
    expect(colorDisplayLabel('yellow'), 'sarı');
    expect(colorDisplayLabel('pink'), 'pembe');
    expect(colorDisplayLabel('purple'), 'mor');
    expect(colorDisplayLabel('orange'), 'turuncu');
    expect(colorDisplayLabel('burgundy'), 'burgundy');
    expect(colorDisplayLabel(''), '');
  });
}
