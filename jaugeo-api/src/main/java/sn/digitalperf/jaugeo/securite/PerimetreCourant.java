package sn.digitalperf.jaugeo.securite;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Perimetre de l'utilisateur connecte : tout le reseau, ou une liste de stations.
 * A utiliser par chaque module pour filtrer les donnees et refuser l'acces hors perimetre.
 */
@Component
public class PerimetreCourant {

    public static final String CLAIM_PERIMETRE = "perimetre";
    public static final String CLAIM_STATIONS = "stations";
    public static final String RESEAU = "RESEAU";

    public boolean estReseau() {
        return RESEAU.equals(jwt().getClaimAsString(CLAIM_PERIMETRE));
    }

    /** Identifiants techniques des stations accessibles (vide si perimetre reseau). */
    public Set<Long> stationIds() {
        List<?> brut = jwt().getClaimAsStringList(CLAIM_STATIONS);
        if (brut == null) {
            return Set.of();
        }
        return brut.stream().map(Object::toString).map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public boolean peutVoirStation(Long stationId) {
        return estReseau() || stationIds().contains(stationId);
    }

    public void exigerStation(Long stationId) {
        if (!peutVoirStation(stationId)) {
            throw ErreurMetier.interdit("Cette station n'est pas dans votre périmètre.");
        }
    }

    public String identifiant() {
        return jwt().getClaimAsString("identifiant");
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw ErreurMetier.interdit("Utilisateur non authentifié.");
    }
}
