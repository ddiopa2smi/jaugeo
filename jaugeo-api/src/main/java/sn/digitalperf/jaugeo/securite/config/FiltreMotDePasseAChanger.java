package sn.digitalperf.jaugeo.securite.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tant qu'un utilisateur utilise un mot de passe provisoire, seules les routes
 * /api/v1/auth/** lui sont ouvertes : il doit d'abord choisir son mot de passe.
 */
class FiltreMotDePasseAChanger extends OncePerRequestFilter {

    static final String CLAIM = "mdp_a_changer";

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM))
                && requete.getRequestURI().startsWith("/api/")
                && !requete.getRequestURI().startsWith("/api/v1/auth/")) {
            SecuriteConfig.ecrireProbleme(reponse, HttpServletResponse.SC_FORBIDDEN, "MOT_DE_PASSE_A_CHANGER",
                    "Vous devez choisir un nouveau mot de passe avant de continuer.");
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
