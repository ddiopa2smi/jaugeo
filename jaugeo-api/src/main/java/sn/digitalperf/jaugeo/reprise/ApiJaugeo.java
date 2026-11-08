package sn.digitalperf.jaugeo.reprise;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client minimal de l'API Jaugeo : la reprise passe par les memes regles que la saisie a l'ecran.
 * Chaque {@link Session} porte un compte ; le jeton d'acces (15 min) est renouvele par reconnexion.
 */
public final class ApiJaugeo {

    /** Refus de l'API : statut HTTP, code metier et message. */
    public static final class Refus extends RuntimeException {
        private final int statut;
        private final String code;

        Refus(int statut, String code, String message) {
            super(message);
            this.statut = statut;
            this.code = code;
        }

        public int statut() {
            return statut;
        }

        public String code() {
            return code;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ApiJaugeo(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public Session session(String identifiant, String motDePasse) {
        Session s = new Session(identifiant, motDePasse);
        s.connecter();
        return s;
    }

    /** Corps JSON a partir de paires cle, valeur (les valeurs nulles sont transmises). */
    public static Map<String, Object> corps(Object... clesValeurs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < clesValeurs.length; i += 2) {
            m.put((String) clesValeurs[i], clesValeurs[i + 1]);
        }
        return m;
    }

    public static String parametre(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    public static BigDecimal decimal(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isMissingNode() || v.isNull() ? null : v.decimalValue();
    }

    public static String texte(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    /** Compte connecte. */
    public final class Session {
        private final String identifiant;
        private String motDePasse;
        private String jeton;

        private Session(String identifiant, String motDePasse) {
            this.identifiant = identifiant;
            this.motDePasse = motDePasse;
        }

        public String identifiant() {
            return identifiant;
        }

        void connecter() {
            HttpResponse<String> r = envoyer(requete("/api/v1/auth/connexion")
                    .POST(json(corps("identifiant", identifiant, "motDePasse", motDePasse))), false);
            jeton = lire(r).path("jetonAcces").asString();
        }

        /** Remplace le mot de passe (provisoire) du compte. */
        public void changerMotDePasse(String nouveau) {
            post("/api/v1/auth/mot-de-passe", corps("ancienMotDePasse", motDePasse, "nouveauMotDePasse", nouveau));
            motDePasse = nouveau;
            connecter();
        }

        public JsonNode get(String chemin) {
            return lire(appeler(chemin, b -> b.GET()));
        }

        public JsonNode post(String chemin, Object corps) {
            return lire(appeler(chemin, b -> b.POST(json(corps))));
        }

        public JsonNode put(String chemin, Object corps) {
            return lire(appeler(chemin, b -> b.PUT(json(corps))));
        }

        /** Envoi d'un fichier (champ « fichier ») en multipart/form-data. */
        public JsonNode fichier(String chemin, String nom, String type, byte[] contenu) {
            String limite = "jaugeo-" + UUID.randomUUID();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            String entete = "--" + limite + "\r\nContent-Disposition: form-data; name=\"fichier\"; filename=\"" + nom
                    + "\"\r\nContent-Type: " + type + "\r\n\r\n";
            o.writeBytes(entete.getBytes(StandardCharsets.UTF_8));
            o.writeBytes(contenu);
            o.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] corps = o.toByteArray();
            return lire(appeler(chemin, b -> b.header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(corps))));
        }

        private HttpResponse<String> appeler(String chemin, java.util.function.UnaryOperator<HttpRequest.Builder> methode) {
            HttpResponse<String> r = envoyer(methode.apply(requete(chemin).header("Authorization", "Bearer " + jeton)), true);
            if (r.statusCode() == 401) {
                connecter();
                r = envoyer(methode.apply(requete(chemin).header("Authorization", "Bearer " + jeton)), true);
            }
            return r;
        }
    }

    private HttpRequest.Builder requete(String chemin) {
        return HttpRequest.newBuilder(URI.create(base + chemin)).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
    }

    private static HttpRequest.BodyPublisher json(Object corps) {
        return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corps), StandardCharsets.UTF_8);
    }

    private HttpResponse<String> envoyer(HttpRequest.Builder b, boolean jsonAttendu) {
        HttpRequest req = b.build();
        if (req.bodyPublisher().isPresent() && req.headers().firstValue("Content-Type").isEmpty()) {
            req = HttpRequest.newBuilder(req, (k, v) -> true).header("Content-Type", "application/json").build();
        }
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Refus(0, "API_INJOIGNABLE", "API injoignable (" + base + ") : " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Refus(0, "INTERROMPU", "Reprise interrompue.");
        }
    }

    private static JsonNode lire(HttpResponse<String> r) {
        String txt = r.body();
        JsonNode n = txt == null || txt.isBlank() ? JSON.createObjectNode() : JSON.readTree(txt);
        if (r.statusCode() >= 400) {
            String detail = texte(n, "detail");
            throw new Refus(r.statusCode(), texte(n, "code") == null ? "HTTP_" + r.statusCode() : texte(n, "code"),
                    detail == null ? r.request().method() + " " + r.request().uri().getPath() + " : HTTP " + r.statusCode() : detail);
        }
        return n;
    }
}
