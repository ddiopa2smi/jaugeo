package sn.digitalperf.jaugeo.commun.config;

import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import org.hibernate.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transmet a PostgreSQL l'auteur de chaque transaction (variable app.utilisateur, portee locale
 * a la transaction). Le declencheur d'audit l'utilise en priorite : ainsi meme les tables sans
 * colonne modifie_par (tables de liaison, suppressions) sont attribuees au bon utilisateur.
 */
@Configuration(proxyBeanMethods = false)
class TransactionsAuditees {

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory emf) {
        return new GestionnaireTransactions(emf);
    }

    static String auteurConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String identifiant = jwt.getClaimAsString("identifiant");
            return identifiant != null ? identifiant : jwt.getSubject();
        }
        return null;
    }

    static final class GestionnaireTransactions extends JpaTransactionManager {

        GestionnaireTransactions(EntityManagerFactory emf) {
            super(emf);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            String auteur = auteurConnecte();
            if (auteur == null) {
                return;
            }
            EntityManagerHolder detenteur =
                    (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            if (detenteur == null) {
                return;
            }
            detenteur.getEntityManager().unwrap(Session.class).doWork(connexion -> {
                try (PreparedStatement ps = connexion.prepareStatement("select set_config('app.utilisateur', ?, true)")) {
                    ps.setString(1, auteur);
                    ps.execute();
                }
            });
        }
    }
}
