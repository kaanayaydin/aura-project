package app.aura.backend;

import app.aura.backend.config.AuraProperties;
import app.aura.backend.config.AuthProperties;
import app.aura.backend.config.ChatProperties;
import app.aura.backend.config.JwtProperties;
import app.aura.backend.config.StorageProperties;
import app.aura.backend.config.VisionProperties;
import app.aura.backend.config.VtonProperties;
import app.aura.backend.config.WeatherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Aura backend servisinin giris noktasi.
 *
 * Bu servis sanal dolabi ve parfum rafini kalici olarak saklar; Vision
 * servisinden gelen kategori ve kesim verilerini kullanici hesabina baglar.
 */
@SpringBootApplication
@EnableConfigurationProperties({
        AuraProperties.class,
        WeatherProperties.class,
        ChatProperties.class,
        VtonProperties.class,
        JwtProperties.class,
        AuthProperties.class,
        StorageProperties.class,
        VisionProperties.class
})
public class AuraBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuraBackendApplication.class, args);
    }
}
