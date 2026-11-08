package sn.digitalperf.jaugeo.securite.application;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.securite.domaine.JetonDepot;
import sn.digitalperf.jaugeo.securite.domaine.Role;
import sn.digitalperf.jaugeo.securite.domaine.RoleDepot;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur.Perimetre;
import sn.digitalperf.jaugeo.securite.domaine.UtilisateurDepot;

/** Gestion des comptes utilisateurs par l'administrateur. */
@Service
@Transactional(readOnly = true)
public class UtilisateurService {

    public record Creation(String identifiant, String nomComplet, String email, String telephone,
                           String roleCode, Perimetre perimetre, Set<Long> stationIds) {
    }

    public record Modification(String nomComplet, String email, String telephone, String roleCode,
                               Perimetre perimetre, Set<Long> stationIds, boolean actif, Integer version) {
    }

    /** Compte cree ou reinitialise : le mot de passe provisoire n'est montre qu'une fois. */
    public record AvecMotDePasseProvisoire(Utilisateur utilisateur, String motDePasseProvisoire) {
    }

    private final UtilisateurDepot utilisateurs;
    private final RoleDepot roles;
    private final JetonDepot jetons;
    private final PasswordEncoder encodeur;
    private final Clock horloge;

    public UtilisateurService(UtilisateurDepot utilisateurs, RoleDepot roles, JetonDepot jetons,
                              PasswordEncoder encodeur, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.roles = roles;
        this.jetons = jetons;
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    public List<Utilisateur> lister() {
        return utilisateurs.findAllByOrderByNomCompletAsc();
    }

    public List<Role> roles() {
        return roles.findAllByOrderByLibelleAsc();
    }

    public Utilisateur trouver(UUID uuid) {
        return utilisateurs.findByUuid(uuid).orElseThrow(() -> ErreurMetier.introuvable("Utilisateur"));
    }

    @Transactional
    public AvecMotDePasseProvisoire creer(Creation c) {
        String identifiant = c.identifiant().trim().toLowerCase();
        if (utilisateurs.trouverParIdentifiant(identifiant).isPresent()) {
            throw ErreurMetier.conflit("IDENTIFIANT_EXISTANT", "L'identifiant « " + identifiant + " » est déjà utilisé.");
        }
        verifierPerimetre(c.perimetre(), c.stationIds());
        String provisoire = PolitiqueMotDePasse.genererProvisoire();
        Utilisateur u = new Utilisateur(identifiant, c.nomComplet().trim(), encodeur.encode(provisoire),
                role(c.roleCode()), c.perimetre());
        u.modifier(c.nomComplet().trim(), vide(c.email()), vide(c.telephone()), role(c.roleCode()),
                c.perimetre(), c.stationIds(), true);
        return new AvecMotDePasseProvisoire(utilisateurs.saveAndFlush(u), provisoire);
    }

    @Transactional
    public Utilisateur modifier(UUID uuid, Modification m, String auteur) {
        Utilisateur u = trouver(uuid);
        u.verifierVersion(m.version());
        verifierPerimetre(m.perimetre(), m.stationIds());
        if (!m.actif() && u.getIdentifiant().equalsIgnoreCase(auteur)) {
            throw ErreurMetier.regle("AUTO_DESACTIVATION", "Vous ne pouvez pas désactiver votre propre compte.");
        }
        u.modifier(m.nomComplet().trim(), vide(m.email()), vide(m.telephone()), role(m.roleCode()),
                m.perimetre(), m.stationIds(), m.actif());
        if (!m.actif()) {
            jetons.revoquerTous(u.getId(), horloge.instant());
        }
        return utilisateurs.saveAndFlush(u);
    }

    @Transactional
    public AvecMotDePasseProvisoire reinitialiserMotDePasse(UUID uuid) {
        Utilisateur u = trouver(uuid);
        String provisoire = PolitiqueMotDePasse.genererProvisoire();
        u.changerMotDePasse(encodeur.encode(provisoire), true);
        jetons.revoquerTous(u.getId(), horloge.instant());
        utilisateurs.enregistrerEchec(u.getId(), 0, null); // deverrouille le compte
        return new AvecMotDePasseProvisoire(utilisateurs.saveAndFlush(trouver(uuid)), provisoire);
    }

    private Role role(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> ErreurMetier.regle("ROLE_INCONNU", "Le rôle « " + code + " » n'existe pas."));
    }

    private static void verifierPerimetre(Perimetre perimetre, Set<Long> stationIds) {
        if (perimetre == Perimetre.STATIONS && (stationIds == null || stationIds.isEmpty())) {
            throw ErreurMetier.regle("PERIMETRE_VIDE",
                    "Un utilisateur limité à des stations doit avoir au moins une station.");
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
