package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Outils communs aux tests d'integration. Toutes les classes partagent le meme contexte Spring,
 * donc la meme base : l'etat du compte admin est lu en base plutot que suppose.
 */
public final class OutilsApi {

    public static final String MDP_INITIAL = "Initial-Test-2026";
    public static final String MDP_ADMIN = "Siege-Jaugeo-2026";
    public static final String MDP_UTILISATEUR = "Nouveau-Mdp-2026";

    private OutilsApi() {
    }

    public static MvcResult connexion(MockMvc mvc, String identifiant, String motDePasse) throws Exception {
        return mvc.perform(post("/api/v1/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"%s\",\"motDePasse\":\"%s\"}".formatted(identifiant, motDePasse)))
                .andReturn();
    }

    public static String jeton(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.jetonAcces");
    }

    public static MockHttpServletRequestBuilder avec(MockHttpServletRequestBuilder req, String jeton) {
        return req.header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON);
    }

    /** Jeton admin ; au premier appel, remplace le mot de passe initial impose. */
    public static synchronized String jetonAdmin(MockMvc mvc, JdbcClient jdbc) throws Exception {
        boolean initial = jdbc.sql("select doit_changer_mot_de_passe from securite.utilisateur where identifiant = 'admin'")
                .query(Boolean.class).single();
        if (initial) {
            MvcResult r = connexion(mvc, "admin", MDP_INITIAL);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            mvc.perform(avec(post("/api/v1/auth/mot-de-passe"), jeton(r))
                            .content("{\"ancienMotDePasse\":\"%s\",\"nouveauMotDePasse\":\"%s\"}".formatted(MDP_INITIAL, MDP_ADMIN)))
                    .andExpect(status().isOk());
        }
        return jeton(connexion(mvc, "admin", MDP_ADMIN));
    }

    /** Cree un utilisateur, remplace son mot de passe provisoire et renvoie son jeton. */
    public static String creerEtConnecter(MockMvc mvc, JdbcClient jdbc, String role, String perimetre, List<Long> stations)
            throws Exception {
        String ident = role.toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
        MvcResult cree = mvc.perform(avec(post("/api/v1/utilisateurs"), jetonAdmin(mvc, jdbc))
                        .content("{\"identifiant\":\"%s\",\"nomComplet\":\"Test %s\",\"role\":\"%s\",\"perimetre\":\"%s\",\"stationIds\":%s}"
                                .formatted(ident, role, role, perimetre, stations.toString())))
                .andExpect(status().isCreated()).andReturn();
        String provisoire = JsonPath.read(cree.getResponse().getContentAsString(), "$.motDePasseProvisoire");
        MvcResult change = mvc.perform(avec(post("/api/v1/auth/mot-de-passe"), jeton(connexion(mvc, ident, provisoire)))
                        .content("{\"ancienMotDePasse\":\"%s\",\"nouveauMotDePasse\":\"%s\"}".formatted(provisoire, MDP_UTILISATEUR)))
                .andExpect(status().isOk()).andReturn();
        return jeton(change);
    }

    public static long numeroStation(JdbcClient jdbc, String code) {
        return jdbc.sql("select id from referentiel.station where code = ?").param(code).query(Long.class).single();
    }

    public static String uuidStation(JdbcClient jdbc, String code) {
        return jdbc.sql("select uuid::text from referentiel.station where code = ?").param(code).query(String.class).single();
    }
}
