package app.aura.backend.engine;

/**
 * Sicakliktan turetilen mevsim / konfor bandi.
 */
public enum SeasonBand {
    HOT,    // > 25 C
    MILD,   // 15-25 C
    COOL,   // 8-15 C
    COLD;   // < 8 C

    public static SeasonBand fromCelsius(double temperatureCelsius) {
        if (temperatureCelsius > 25.0) {
            return HOT;
        }
        if (temperatureCelsius >= 15.0) {
            return MILD;
        }
        if (temperatureCelsius >= 8.0) {
            return COOL;
        }
        return COLD;
    }

    public String label() {
        return switch (this) {
            case HOT -> "hot";
            case MILD -> "mild";
            case COOL -> "cool";
            case COLD -> "cold";
        };
    }
}
