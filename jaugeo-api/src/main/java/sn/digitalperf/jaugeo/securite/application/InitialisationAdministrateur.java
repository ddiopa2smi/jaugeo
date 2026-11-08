package sn.digitalperf.jaugeo.securite.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.securite.config.ProprietesSecurite;
import sn.digitalperf.jaugeo.securite.domaine.RoleDepot;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;
import sn.digitalperf.jaugeo.securite.domaine.UtilisateurDepot;

/**
 * Au premier demarrage (aucun utilisateur), cree le compte « admin » avec le mot de passe
 * initial configure ; il devra etre change a la premiere connexion.
 */
@Component
class InitialisationAdministrateur implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialisationAdministrateur.class);

    private final UtilisateurDepot utilisateurs;
    private final RoleDepot roles;
    private final PasswordEncoder encodeur;
    private final ProprietesSecurite proprietes;

    InitialisationAdministrateur(UtilisateurDepot utilisateurs, RoleDepot roles, PasswordEncoder encodeur,
                                 ProprietesSecurite proprietes) {
        this.utilisateurs = utilisateurs;
        this.roles = roles;
        this.encodeur = encodeur;
        this.proprietes = proprietes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (utilisateurs.count() > 0) {
            return;
        }
        Utilisateur admin = new Utilisateur("admin", "Administrateur Jaugeo",
                encodeur.encode(proprietes.adminMotDePasseInitial()),
                roles.findByCode("ADMINISTRATEUR").orElseThrow(), Utilisateur.Perimetre.RESEAU);
        utilisateurs.save(admin);
        LOG.warn("Compte 'admin' cree avec le mot de passe initial configure : il devra etre change a la premiere connexion.");
    }
}
