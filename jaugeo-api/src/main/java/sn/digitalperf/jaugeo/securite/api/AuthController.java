package sn.digitalperf.jaugeo.securite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.securite.application.AuthService;
import sn.digitalperf.jaugeo.securite.application.AuthService.Session;
import sn.digitalperf.jaugeo.securite.application.UtilisateurService;
import sn.digitalperf.jaugeo.securite.config.ProprietesSecurite;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;

@Tag(name = "Authentification")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String COOKIE = "jaugeo_rt";
    private static final String CHEMIN_COOKIE = "/api/v1/auth";

    record Connexion(@NotBlank(message = "L'identifiant est obligatoire") @Size(max = 50) String identifiant,
                     @NotBlank(message = "Le mot de passe est obligatoire") @Size(max = 128) String motDePasse) {
    }

    record ChangementMotDePasse(@NotBlank String ancienMotDePasse,
                                @NotBlank @Size(max = 128) String nouveauMotDePasse) {
    }

    record UtilisateurConnecte(UUID id, String identifiant, String nomComplet, String role, String roleLibelle,
                               List<String> permissions, String perimetre, List<Long> stationIds,
                               boolean doitChangerMotDePasse) {
        static UtilisateurConnecte de(Utilisateur u) {
            return new UtilisateurConnecte(u.getUuid(), u.getIdentifiant(), u.getNomComplet(), u.getRole().getCode(),
                    u.getRole().getLibelle(), u.getRole().getPermissions().stream().sorted().toList(),
                    u.getPerimetre().name(), u.getStationIds().stream().sorted().toList(),
                    u.isDoitChangerMotDePasse());
        }
    }

    record ReponseSession(String jetonAcces, long expireDans, UtilisateurConnecte utilisateur) {
    }

    private final AuthService auth;
    private final UtilisateurService utilisateurs;
    private final ProprietesSecurite proprietes;

    AuthController(AuthService auth, UtilisateurService utilisateurs, ProprietesSecurite proprietes) {
        this.auth = auth;
        this.utilisateurs = utilisateurs;
        this.proprietes = proprietes;
    }

    @Operation(summary = "Connexion : renvoie un jeton d'accès et pose le cookie de rafraîchissement")
    @PostMapping("/connexion")
    ReponseSession connexion(@Valid @RequestBody Connexion c, HttpServletRequest requete, HttpServletResponse reponse) {
        return repondre(auth.connecter(c.identifiant(), c.motDePasse(), requete.getRemoteAddr()), reponse);
    }

    @Operation(summary = "Nouveau jeton d'accès à partir du cookie de rafraîchissement (rotation)")
    @PostMapping("/rafraichir")
    ReponseSession rafraichir(@CookieValue(name = COOKIE, required = false) String jeton, HttpServletResponse reponse) {
        try {
            return repondre(auth.rafraichir(jeton), reponse);
        } catch (RuntimeException e) {
            effacerCookie(reponse);
            throw e;
        }
    }

    @Operation(summary = "Déconnexion : révoque la session de cet appareil")
    @PostMapping("/deconnexion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deconnexion(@CookieValue(name = COOKIE, required = false) String jeton, HttpServletResponse reponse) {
        auth.deconnecter(jeton);
        effacerCookie(reponse);
    }

    @Operation(summary = "Utilisateur connecté")
    @GetMapping("/moi")
    UtilisateurConnecte moi(@AuthenticationPrincipal Jwt jwt) {
        return UtilisateurConnecte.de(utilisateurs.trouver(UUID.fromString(jwt.getSubject())));
    }

    @Operation(summary = "Changer son mot de passe (ferme les autres sessions)")
    @PostMapping("/mot-de-passe")
    ReponseSession changerMotDePasse(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangementMotDePasse c,
                                     HttpServletResponse reponse) {
        return repondre(auth.changerMotDePasse(UUID.fromString(jwt.getSubject()), c.ancienMotDePasse(),
                c.nouveauMotDePasse()), reponse);
    }

    private ReponseSession repondre(Session s, HttpServletResponse reponse) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, s.jetonRafraichissement())
                .httpOnly(true)
                .secure(proprietes.cookieSecurise())
                .sameSite("Strict")
                .path(CHEMIN_COOKIE)
                .maxAge(proprietes.dureeJetonRafraichissement())
                .build();
        reponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        reponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new ReponseSession(s.jetonAcces(), s.expireDans(), UtilisateurConnecte.de(s.utilisateur()));
    }

    private void effacerCookie(HttpServletResponse reponse) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, "")
                .httpOnly(true).secure(proprietes.cookieSecurise()).sameSite("Strict")
                .path(CHEMIN_COOKIE).maxAge(Duration.ZERO).build();
        reponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
