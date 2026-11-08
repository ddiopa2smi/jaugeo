package sn.digitalperf.jaugeo.securite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.securite.config.ProprietesSecurite;
import sn.digitalperf.jaugeo.securite.domaine.JetonDepot;
import sn.digitalperf.jaugeo.securite.domaine.JetonRafraichissement;
import sn.digitalperf.jaugeo.securite.domaine.JournalConnexion;
import sn.digitalperf.jaugeo.securite.domaine.JournalConnexionDepot;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;
import sn.digitalperf.jaugeo.securite.domaine.UtilisateurDepot;

/**
 * Connexion, rafraichissement (avec rotation et detection de rejeu), deconnexion,
 * changement de mot de passe, verrouillage apres echecs repetes.
 */
@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final String MESSAGE_ECHEC = "Identifiant ou mot de passe incorrect.";

    /** Resultat d'une authentification : jeton d'acces + jeton de rafraichissement (a poser en cookie). */
    public record Session(String jetonAcces, long expireDans, String jetonRafraichissement, Utilisateur utilisateur) {
    }

    private final UtilisateurDepot utilisateurs;
    private final JetonDepot jetons;
    private final JournalConnexionDepot journal;
    private final PasswordEncoder encodeur;
    private final JetonService jetonService;
    private final ProprietesSecurite proprietes;
    private final Clock horloge;
    // Hash factice : la verification prend le meme temps que l'utilisateur existe ou non
    private final String hashFactice;

    public AuthService(UtilisateurDepot utilisateurs, JetonDepot jetons, JournalConnexionDepot journal,
                       PasswordEncoder encodeur, JetonService jetonService, ProprietesSecurite proprietes,
                       Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.jetons = jetons;
        this.journal = journal;
        this.encodeur = encodeur;
        this.jetonService = jetonService;
        this.proprietes = proprietes;
        this.horloge = horloge;
        this.hashFactice = encodeur.encode("jaugeo-hash-factice-" + UUID.randomUUID());
    }

    /**
     * Pas de transaction englobante : le compteur d'echecs et le journal doivent etre
     * enregistres meme quand la connexion est refusee.
     */
    public Session connecter(String identifiant, String motDePasse, String adresseIp) {
        Instant maintenant = horloge.instant();
        String ident = identifiant == null ? "" : identifiant.trim();
        Utilisateur u = utilisateurs.trouverParIdentifiant(ident).orElse(null);

        if (u == null) {
            encodeur.matches(motDePasse, hashFactice);
            tracer(ident, false, "IDENTIFIANT_INCONNU", adresseIp, maintenant);
            throw nonAutorise("IDENTIFIANTS_INVALIDES", MESSAGE_ECHEC);
        }
        if (!u.isActif()) {
            tracer(ident, false, "COMPTE_DESACTIVE", adresseIp, maintenant);
            throw nonAutorise("COMPTE_DESACTIVE", "Ce compte est désactivé. Contactez l'administrateur.");
        }
        if (u.estVerrouille(maintenant)) {
            tracer(ident, false, "COMPTE_VERROUILLE", adresseIp, maintenant);
            throw nonAutorise("COMPTE_VERROUILLE",
                    "Compte verrouillé après plusieurs échecs. Réessayez dans quelques minutes.");
        }
        if (!encodeur.matches(motDePasse, u.getMotDePasseHash())) {
            int echecs = u.getEchecsConnexion() + 1;
            Instant verrou = echecs >= proprietes.tentativesMax() ? maintenant.plus(proprietes.dureeVerrouillage()) : null;
            utilisateurs.enregistrerEchec(u.getId(), verrou != null ? 0 : echecs, verrou);
            tracer(ident, false, verrou != null ? "VERROUILLAGE" : "MOT_DE_PASSE_INCORRECT", adresseIp, maintenant);
            if (verrou != null) {
                LOG.warn("Compte {} verrouille apres {} echecs", ident, echecs);
                throw nonAutorise("COMPTE_VERROUILLE",
                        "Trop de tentatives : compte verrouillé pendant "
                                + proprietes.dureeVerrouillage().toMinutes() + " minutes.");
            }
            throw nonAutorise("IDENTIFIANTS_INVALIDES", MESSAGE_ECHEC);
        }

        utilisateurs.enregistrerSucces(u.getId(), maintenant);
        tracer(ident, true, null, adresseIp, maintenant);
        return ouvrirSession(utilisateurs.findById(u.getId()).orElseThrow(), maintenant);
    }

    // noRollbackFor : la revocation de toutes les sessions en cas de rejeu doit etre conservee
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Session rafraichir(String jetonRafraichissement) {
        Instant maintenant = horloge.instant();
        if (jetonRafraichissement == null || jetonRafraichissement.isBlank()) {
            throw nonAutorise("SESSION_EXPIREE", "Session expirée. Reconnectez-vous.");
        }
        JetonRafraichissement jeton = jetons.findByEmpreinte(JetonService.empreinte(jetonRafraichissement))
                .orElseThrow(() -> nonAutorise("SESSION_EXPIREE", "Session expirée. Reconnectez-vous."));
        Utilisateur u = jeton.getUtilisateur();

        if (jeton.getRevoqueLe() != null) {
            // Rejeu d'un jeton deja utilise : vol probable, on coupe toutes les sessions de l'utilisateur
            LOG.warn("Rejeu d'un jeton de rafraichissement revoque pour {} : toutes ses sessions sont fermees",
                    u.getIdentifiant());
            jetons.revoquerTous(u.getId(), maintenant);
            throw nonAutorise("SESSION_EXPIREE", "Session expirée. Reconnectez-vous.");
        }
        if (!jeton.estUtilisable(maintenant) || !u.isActif()) {
            throw nonAutorise("SESSION_EXPIREE", "Session expirée. Reconnectez-vous.");
        }
        jeton.revoquer(maintenant); // rotation : chaque jeton ne sert qu'une fois
        return ouvrirSession(u, maintenant);
    }

    @Transactional
    public void deconnecter(String jetonRafraichissement) {
        if (jetonRafraichissement == null || jetonRafraichissement.isBlank()) {
            return;
        }
        jetons.findByEmpreinte(JetonService.empreinte(jetonRafraichissement))
                .ifPresent(j -> j.revoquer(horloge.instant()));
    }

    @Transactional
    public Session changerMotDePasse(UUID utilisateurUuid, String ancien, String nouveau) {
        Utilisateur u = utilisateurs.findByUuid(utilisateurUuid).orElseThrow(() -> ErreurMetier.introuvable("Utilisateur"));
        if (!encodeur.matches(ancien, u.getMotDePasseHash())) {
            throw ErreurMetier.regle("ANCIEN_MOT_DE_PASSE_INCORRECT", "Le mot de passe actuel est incorrect.");
        }
        if (encodeur.matches(nouveau, u.getMotDePasseHash())) {
            throw ErreurMetier.regle("MOT_DE_PASSE_IDENTIQUE", "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        PolitiqueMotDePasse.verifier(nouveau, u.getIdentifiant());
        u.changerMotDePasse(encodeur.encode(nouveau), false);
        Instant maintenant = horloge.instant();
        // Toutes les autres sessions sont fermees ; une nouvelle session est ouverte pour l'appareil courant
        jetons.revoquerTous(u.getId(), maintenant);
        utilisateurs.saveAndFlush(u);
        return ouvrirSession(u, maintenant);
    }

    private Session ouvrirSession(Utilisateur u, Instant maintenant) {
        String rafraichissement = jetonService.nouveauJetonRafraichissement();
        jetons.save(new JetonRafraichissement(u, JetonService.empreinte(rafraichissement), maintenant,
                maintenant.plus(proprietes.dureeJetonRafraichissement())));
        return new Session(jetonService.emettreJetonAcces(u), jetonService.dureeJetonAccesSecondes(),
                rafraichissement, u);
    }

    private void tracer(String identifiant, boolean succes, String motif, String ip, Instant maintenant) {
        String ident = identifiant.length() > 50 ? identifiant.substring(0, 50) : identifiant;
        journal.save(new JournalConnexion(maintenant, ident, succes, motif, ip));
    }

    private static ErreurMetier nonAutorise(String code, String message) {
        return new ErreurMetier(HttpStatus.UNAUTHORIZED, code, message);
    }
}
