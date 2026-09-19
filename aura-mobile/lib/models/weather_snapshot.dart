/// Backend `WeatherSnapshot` eslemesi.
class WeatherSnapshot {
  const WeatherSnapshot({
    required this.temperatureCelsius,
    required this.humidityPercent,
    required this.condition,
    required this.weatherCode,
    required this.latitude,
    required this.longitude,
    required this.locationName,
    required this.source,
  });

  final double temperatureCelsius;
  final double humidityPercent;
  final String condition;
  final int weatherCode;
  final double latitude;
  final double longitude;
  final String locationName;
  final String source;

  factory WeatherSnapshot.fromJson(Map<String, dynamic> json) {
    return WeatherSnapshot(
      temperatureCelsius: (json['temperatureCelsius'] as num?)?.toDouble() ?? 0,
      humidityPercent: (json['humidityPercent'] as num?)?.toDouble() ?? 0,
      condition: json['condition'] as String? ?? '',
      weatherCode: (json['weatherCode'] as num?)?.toInt() ?? 0,
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0,
      locationName: json['locationName'] as String? ?? '',
      source: json['source'] as String? ?? '',
    );
  }

  String get sourceLabel => source == 'open-meteo'
      ? 'Canli'
      : source == 'simulated'
          ? 'Tahmini'
          : source;
}
