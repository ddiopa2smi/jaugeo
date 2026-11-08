package sn.digitalperf.jaugeo.securite.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.securite.config.ProprietesSecurite;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;

/** Emission des jetons d'acces (JWT signes HS256) et des jetons de rafraichissement (aleatoires). */
@Service
public class JetonService {

    private static final SecureRandom ALEA = new SecureRandom();

    private final JwtEncoder encodeur;
    private final ProprietesSecurite proprietes;
    private final Clock horloge;

    public JetonService(JwtEncoder encodeur, ProprietesSecurite proprietes, Clock horloge) {
        this.encodeur = encodeur;
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    public String emettreJetonAcces(Utilisateur u) {
        Instant maintenant = horloge.instant();
        List<String> permissions = u.getRole().getPermissions().stream().sorted().toList();
        List<String> stations = u.getStationIds().stream().sorted().map(String::valueOf).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(proprietes.jwtEmetteur())
                .subject(u.getUuid().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(proprietes.dureeJetonAcces()))
                .claim("identifiant", u.getIdentifiant())
                .claim("nom", u.getNomComplet())
                .claim("role", u.getRole().getCode())
                .claim("permissions", permissions)
                .claim(PerimetreCourant.CLAIM_PERIMETRE, u.getPerimetre().name())
                .claim(PerimetreCourant.CLAIM_STATIONS, stations)
                .claim("mdp_a_changer", u.isDoitChangerMotDePasse())
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encodeur.encode(JwtEncoderParameters.from(entete, claims)).getTokenValue();
    }

    public long dureeJetonAccesSecondes() {
        return proprietes.dureeJetonAcces().toSeconds();
    }

    /** Jeton de rafraichissement : 256 bits aleatoires, encodes en base64url. */
    public String nouveauJetonRafraichissement() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    public static String empreinte(String jeton) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
