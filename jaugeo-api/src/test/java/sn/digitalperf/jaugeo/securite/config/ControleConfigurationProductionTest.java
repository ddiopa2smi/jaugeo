package sn.digitalperf.jaugeo.securite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ControleConfigurationProductionTest {

    private static ProprietesSecurite proprietes(String secret, boolean cookieSecurise, String admin) {
        return new ProprietesSecurite(secret, "jaugeo", Duration.ofMinutes(15), Duration.ofHours(8), cookieSecurise, 5,
                Duration.ofMinutes(15), admin);
    }

    private static final String SECRET_DEV = "dev-uniquement-changez-moi-secret-jaugeo-hs256-2026";
    private static final String SECRET_PROD = "q8Zt3Vw0Lr6Nk2Hy9Bd5Jm1Xc7Fp4Gs0Ta8Ue2Wi6Yo";

    @Test
    void laConfigurationDeDeveloppementEstRefuseeEnProduction() {
        assertThat(ControleConfigurationProduction.problemes(proprietes(SECRET_DEV, false, "Jaugeo@2026")))
                .hasSize(3)
                .anySatisfy(p -> assertThat(p).contains("JAUGEO_JWT_SECRET"))
                .anySatisfy(p -> assertThat(p).contains("JAUGEO_COOKIE_SECURE"))
                .anySatisfy(p -> assertThat(p).contains("JAUGEO_ADMIN_PASSWORD"));
        assertThat(ControleConfigurationProduction.problemes(proprietes("a".repeat(40), true, "Autre-Mdp-2027")))
                .containsExactly("JAUGEO_JWT_SECRET est trop peu varié pour être aléatoire");
        assertThat(ControleConfigurationProduction.problemes(proprietes(SECRET_PROD, true, "Autre-Mdp-2027"))).isEmpty();
    }

    @Test
    void leDemarrageEstBloqueHorsDeveloppementSeulement() {
        ProprietesSecurite dev = proprietes(SECRET_DEV, false, "Jaugeo@2026");

        MockEnvironment developpement = new MockEnvironment();
        developpement.setDefaultProfiles("dev");
        new ControleConfigurationProduction(developpement, dev).afterPropertiesSet();

        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ControleConfigurationProduction(production, dev).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profil prod");
        new ControleConfigurationProduction(production, proprietes(SECRET_PROD, true, "Autre-Mdp-2027")).afterPropertiesSet();
    }
}
