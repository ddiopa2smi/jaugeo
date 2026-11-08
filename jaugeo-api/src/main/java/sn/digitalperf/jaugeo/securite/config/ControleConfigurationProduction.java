package sn.digitalperf.jaugeo.securite.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuse de demarrer hors developpement (profil autre que dev ou test) avec une configuration de developpement :
 * secret JWT de dev ou trop court, cookie de rafraichissement non securise (HTTP), mot de passe initial de
 * l'administrateur par defaut.
 */
@Component
class ControleConfigurationProduction implements InitializingBean {

    static final String SECRET_DEV = "dev-uniquement";
    static final String MOT_DE_PASSE_DEFAUT = "Jaugeo@2026";
    private static final Set<String> PROFILS_DEV = Set.of("dev", "test");

    private final Environment environnement;
    private final ProprietesSecurite proprietes;

    ControleConfigurationProduction(Environment environnement, ProprietesSecurite proprietes) {
        this.environnement = environnement;
        this.proprietes = proprietes;
    }

    @Override
    public void afterPropertiesSet() {
        String[] actifs = environnement.getActiveProfiles();
        List<String> profils = actifs.length > 0 ? Arrays.asList(actifs) : Arrays.asList(environnement.getDefaultProfiles());
        if (profils.stream().anyMatch(PROFILS_DEV::contains)) {
            return;
        }
        List<String> problemes = problemes(proprietes);
        if (!problemes.isEmpty()) {
            throw new IllegalStateException("Configuration de production refusée (profil " + String.join(",", profils)
                    + ") : " + String.join(" ; ", problemes));
        }
    }

    static List<String> problemes(ProprietesSecurite p) {
        List<String> r = new ArrayList<>();
        String secret = p.jwtSecret() == null ? "" : p.jwtSecret();
        if (secret.contains(SECRET_DEV) || secret.length() < 32) {
            r.add("JAUGEO_JWT_SECRET doit être un secret aléatoire d'au moins 32 caractères, propre à l'installation");
        } else if (secret.chars().distinct().count() < 10) {
            r.add("JAUGEO_JWT_SECRET est trop peu varié pour être aléatoire");
        }
        if (!p.cookieSecurise()) {
            r.add("JAUGEO_COOKIE_SECURE doit valoir true (application servie en HTTPS)");
        }
        if (MOT_DE_PASSE_DEFAUT.equals(p.adminMotDePasseInitial())) {
            r.add("JAUGEO_ADMIN_PASSWORD ne doit pas être le mot de passe initial de développement");
        }
        return r;
    }
}
