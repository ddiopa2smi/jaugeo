package sn.digitalperf.jaugeo.securite.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Parametres de securite (section jaugeo.securite du fichier application.yml). */
@Validated
@ConfigurationProperties(prefix = "jaugeo.securite")
public record ProprietesSecurite(
        @NotBlank @Size(min = 32, message = "Le secret JWT doit faire au moins 32 caractères (256 bits)") String jwtSecret,
        @NotBlank String jwtEmetteur,
        @NotNull Duration dureeJetonAcces,
        @NotNull Duration dureeJetonRafraichissement,
        boolean cookieSecurise,
        @Min(1) int tentativesMax,
        @NotNull Duration dureeVerrouillage,
        @NotBlank String adminMotDePasseInitial) {
}
