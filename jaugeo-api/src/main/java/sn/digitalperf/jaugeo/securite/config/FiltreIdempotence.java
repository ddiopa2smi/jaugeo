package sn.digitalperf.jaugeo.securite.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * En-tete {@code Idempotency-Key} : la tablette rejoue a la reconnexion les saisies faites hors ligne. Une requete
 * deja traitee pour le meme utilisateur et la meme cle n'est pas executee de nouveau : sa reponse d'origine est
 * renvoyee (en-tete {@code Idempotency-Replayed: true}).
 *
 * <ul>
 *   <li>meme cle, autre requete (methode, chemin ou corps differents) : 422 {@code IDEMPOTENCE_CLE_REUTILISEE} ;</li>
 *   <li>meme cle pendant que la premiere requete s'execute : 409 {@code REQUETE_EN_COURS} ;</li>
 *   <li>reponses passageres (401, 403, 429, 5xx) non memorisees : la requete pourra etre rejouee.</li>
 * </ul>
 * Seules les ecritures JSON authentifiees sont concernees (pas les envois de fichiers).
 */
class FiltreIdempotence extends OncePerRequestFilter {

    static final String ENTETE = "Idempotency-Key";
    static final String ENTETE_REJOUEE = "Idempotency-Replayed";
    private static final Pattern CLE = Pattern.compile("[A-Za-z0-9_-]{8,100}");
    private static final Set<String> METHODES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<Integer> PASSAGERS = Set.of(401, 403, 408, 429);
    private static final int TAILLE_MAX = 1 << 20;

    private final JdbcClient jdbc;
    private final AtomicInteger insertions = new AtomicInteger();

    FiltreIdempotence(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String type = requete.getContentType();
        return requete.getHeader(ENTETE) == null || !METHODES.contains(requete.getMethod())
                || (type != null && type.toLowerCase().startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String cle = requete.getHeader(ENTETE).trim();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof Jwt jwt)) {
            chaine.doFilter(requete, reponse);
            return;
        }
        if (!CLE.matcher(cle).matches()) {
            SecuriteConfig.ecrireProbleme(reponse, 400, "IDEMPOTENCE_CLE_INVALIDE",
                    "En-tête Idempotency-Key invalide : 8 à 100 caractères parmi lettres, chiffres, - et _.");
            return;
        }
        byte[] corps = requete.getInputStream().readNBytes(TAILLE_MAX + 1);
        if (corps.length > TAILLE_MAX) {
            SecuriteConfig.ecrireProbleme(reponse, 413, "REQUETE_TROP_VOLUMINEUSE", "Requête trop volumineuse.");
            return;
        }
        String utilisateur = jwt.getSubject();
        String chemin = requete.getRequestURI() + (requete.getQueryString() == null ? "" : "?" + requete.getQueryString());
        String empreinte = empreinte(requete.getMethod(), chemin, corps);

        Optional<Memorisee> deja = lire(utilisateur, cle);
        if (deja.isEmpty() && reserver(utilisateur, cle, requete.getMethod(), chemin, empreinte)) {
            executer(new RequeteRelue(requete, corps), reponse, chaine, utilisateur, cle);
            return;
        }
        Memorisee m = deja.or(() -> lire(utilisateur, cle)).orElse(null);
        if (m == null || m.statut() == null) {
            SecuriteConfig.ecrireProbleme(reponse, 409, "REQUETE_EN_COURS",
                    "Cette saisie est déjà en cours d'envoi. Réessayez dans un instant.");
        } else if (!m.empreinte().equals(empreinte)) {
            SecuriteConfig.ecrireProbleme(reponse, 422, "IDEMPOTENCE_CLE_REUTILISEE",
                    "Cette clé d'envoi a déjà servi pour une autre saisie.");
        } else {
            reponse.setStatus(m.statut());
            reponse.setHeader(ENTETE_REJOUEE, "true");
            if (m.typeContenu() != null) {
                reponse.setContentType(m.typeContenu());
            }
            if (m.corps() != null) {
                reponse.setContentLength(m.corps().length);
                reponse.getOutputStream().write(m.corps());
            }
        }
    }

    private void executer(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine, String utilisateur,
                          String cle) throws ServletException, IOException {
        ContentCachingResponseWrapper enveloppe = new ContentCachingResponseWrapper(reponse);
        boolean memorisee = false;
        try {
            chaine.doFilter(requete, enveloppe);
            int statut = enveloppe.getStatus();
            if (statut < 500 && !PASSAGERS.contains(statut)) {
                jdbc.sql("update securite.requete_idempotente set statut = ?, type_contenu = ?, corps = ? "
                                + "where utilisateur = ? and cle = ?")
                        .params(statut, enveloppe.getContentType(), enveloppe.getContentAsByteArray(), utilisateur, cle)
                        .update();
                memorisee = true;
            }
        } finally {
            if (!memorisee) {
                jdbc.sql("delete from securite.requete_idempotente where utilisateur = ? and cle = ?")
                        .params(utilisateur, cle).update();
            }
            enveloppe.copyBodyToResponse();
        }
    }

    private boolean reserver(String utilisateur, String cle, String methode, String chemin, String empreinte) {
        if (insertions.incrementAndGet() % 200 == 0) {
            jdbc.sql("delete from securite.requete_idempotente where cree_le < now() - interval '7 days'").update();
        }
        return jdbc.sql("insert into securite.requete_idempotente (utilisateur, cle, methode, chemin, empreinte) "
                        + "values (?, ?, ?, ?, ?) on conflict do nothing")
                .params(utilisateur, cle, methode, chemin.length() > 500 ? chemin.substring(0, 500) : chemin, empreinte)
                .update() == 1;
    }

    private Optional<Memorisee> lire(String utilisateur, String cle) {
        return jdbc.sql("select statut, empreinte, type_contenu, corps from securite.requete_idempotente "
                        + "where utilisateur = ? and cle = ?")
                .params(utilisateur, cle)
                .query((rs, i) -> new Memorisee((Integer) rs.getObject("statut"), rs.getString("empreinte"),
                        rs.getString("type_contenu"), rs.getBytes("corps")))
                .optional();
    }

    private static String empreinte(String methode, String chemin, byte[] corps) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((methode + ' ' + chemin + '\n').getBytes(StandardCharsets.UTF_8));
            sha.update(corps);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Memorisee(Integer statut, String empreinte, String typeContenu, byte[] corps) {
    }

    /** Requete dont le corps, deja lu pour l'empreinte, est relu par le controleur. */
    private static final class RequeteRelue extends HttpServletRequestWrapper {
        private final byte[] corps;

        RequeteRelue(HttpServletRequest requete, byte[] corps) {
            super(requete);
            this.corps = corps;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream flux = new ByteArrayInputStream(corps);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return flux.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener ecouteur) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return flux.read();
                }

                @Override
                public int read(byte[] b, int debut, int longueur) {
                    return flux.read(b, debut, longueur);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return corps.length;
        }

        @Override
        public long getContentLengthLong() {
            return corps.length;
        }
    }
}
