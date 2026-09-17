package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * `aura.*` altindaki uygulama ayarlari.
 *
 * @param defaultUsername istekte userId gelmediginde kullanilacak kullanici adi
 * @param maxImageBytes   kabul edilen en buyuk cozulmus gorsel boyutu
 */
@ConfigurationProperties(prefix = "aura.wardrobe")
public record AuraProperties(String defaultUsername, int maxImageBytes) {

    public AuraProperties {
        if (defaultUsername == null || defaultUsername.isBlank()) {
            defaultUsername = "demo";
        }
        if (maxImageBytes <= 0) {
            maxImageBytes = 10 * 1024 * 1024;
        }
    }
}
