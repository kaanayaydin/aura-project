/// Open-Meteo / Java `WeatherService.conditionLabel` değerini Türkçe gösterir.
///
/// Yalnızca ekranda kullanılır. API'den gelen `condition` değişmez.
String weatherConditionDisplayLabel(String condition) {
  final key = condition.trim().toLowerCase();
  if (key.isEmpty) return condition;
  return switch (key) {
    'clear' => 'Açık',
    'partly cloudy' => 'Parçalı bulutlu',
    'fog' => 'Sisli',
    'rain' => 'Yağmurlu',
    'snow' => 'Karlı',
    'showers' => 'Sağanak yağışlı',
    'thunderstorm' => 'Gök gürültülü fırtına',
    'unknown' => 'Bilinmiyor',
    _ => condition.trim(),
  };
}
