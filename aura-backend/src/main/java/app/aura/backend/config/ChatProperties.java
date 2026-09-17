package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lokal LLM (Ollama) sohbet ayarlari.
 *
 * @param ollamaBaseUrl     ornek: http://127.0.0.1:11434
 * @param model             llama3.2 / mistral / vs.
 * @param timeoutSeconds    Ollama okuma zaman asimi
 * @param temperature       uretim sicakligi (0-1)
 * @param fallbackEnabled   Ollama yoksa baglam tabanli yerel yanit
 */
@ConfigurationProperties(prefix = "aura.chat")
public record ChatProperties(
        String ollamaBaseUrl,
        String model,
        int timeoutSeconds,
        double temperature,
        boolean fallbackEnabled) {

    public ChatProperties {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
            ollamaBaseUrl = "http://127.0.0.1:11434";
        }
        if (model == null || model.isBlank()) {
            model = "llama3.2";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 90;
        }
        if (temperature <= 0) {
            temperature = 0.35;
        }
        if (temperature > 1.5) {
            temperature = 1.5;
        }
    }
}
