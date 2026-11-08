package sn.digitalperf.jaugeo.commun.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Renseigne cree_par / modifie_par avec l'identifiant de l'utilisateur connecte.
 * Le declencheur d'audit PostgreSQL reprend cette valeur dans le journal.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auteurCourant")
class JpaConfig {

    static final String SYSTEME = "systeme";

    @Bean
    AuditorAware<String> auteurCourant() {
        return () -> Optional.ofNullable(TransactionsAuditees.auteurConnecte()).or(() -> Optional.of(SYSTEME));
    }
}
