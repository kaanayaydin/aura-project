import 'package:aura_mobile/core/weather_condition_labels.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('weatherConditionDisplayLabel WMO etiketlerini Turkceye cevirir', () {
    expect(weatherConditionDisplayLabel('Clear'), 'Açık');
    expect(weatherConditionDisplayLabel('partly cloudy'), 'Parçalı bulutlu');
    expect(weatherConditionDisplayLabel('Fog'), 'Sisli');
    expect(weatherConditionDisplayLabel('Rain'), 'Yağmurlu');
    expect(weatherConditionDisplayLabel('Snow'), 'Karlı');
    expect(weatherConditionDisplayLabel('Showers'), 'Sağanak yağışlı');
    expect(weatherConditionDisplayLabel('Thunderstorm'), 'Gök gürültülü fırtına');
    expect(weatherConditionDisplayLabel('Unknown'), 'Bilinmiyor');
    expect(weatherConditionDisplayLabel('Manual'), 'Manual');
    expect(weatherConditionDisplayLabel(''), '');
  });
}
