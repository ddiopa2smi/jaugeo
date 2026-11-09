package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Lot 2, sprint M5 : encaissements electroniques deduits des especes a verser, plafond d'especes.
 * Station reservee a ces tests : TRM (creee ici, gasoil a 755 F, bareme de 10 L/mm).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class TresorerieM5Test {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    String station;
    String cuve;
    String gerant;

    private String lire(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String creer(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(post(url), jeton).content(corps)).andExpect(status().isCreated()));
    }

    private String modifier(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(put(url), jeton).content(corps)).andExpect(status().isOk()));
    }

    private String lireGet(String jeton, String url) throws Exception {
        return lire(mvc.perform(avec(get(url), jeton)).andExpect(status().isOk()));
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString());
    }

    private void creerStation(String code) throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"" + code + "\",\"nom\":\"Station trésorerie\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String c = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}");
        cuve = JsonPath.read(c, "$.id");
        modifier(admin, "/api/v1/cuves/" + cuve + "/bareme", """
                {"certificat":"CERT-%s","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}"""
                .formatted(code, JsonPath.read(c, "$.version").toString()));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(cuve));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test M5","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755}],"confirmerVariation":true}""".formatted(station));
        gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, code)));
    }

    /** Journee de 100 L (75 500 F), jaugeages sans ecart ; renvoie l'identifiant de la journee. */
    private String journee(String date, boolean ouverture) throws Exception {
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + date + "\"}");
        String id = JsonPath.read(j, "$.id");
        Map<String, Object> ligne = JsonPath.<List<Map<String, Object>>>read(j, "$.lignes").get(0);
        BigDecimal depart = new BigDecimal(ligne.get("indexDepart").toString());
        j = modifier(gerant, "/api/v1/journees/" + id + "/releves", "{\"version\":%s,\"releves\":[{\"pistoletId\":\"%s\",\"indexArrivee\":%s}]}"
                .formatted(JsonPath.read(j, "$.version").toString(), ligne.get("pistoletId"), depart.add(BigDecimal.valueOf(100)).toPlainString()));
        int hauteurFin = 1000 - 10 * (int) (depart.subtract(BigDecimal.valueOf(1000)).longValue() / 100) - 10;
        String jaugeages = (ouverture ? "{\"cuveId\":\"%1$s\",\"moment\":\"OUVERTURE\",\"hauteurMm\":1000}," : "")
                + "{\"cuveId\":\"%1$s\",\"moment\":\"CLOTURE\",\"hauteurMm\":" + hauteurFin + "}";
        modifier(gerant, "/api/v1/journees/" + id + "/jaugeages", "{\"version\":%s,\"jaugeages\":[%s]}"
                .formatted(JsonPath.read(j, "$.version").toString(), jaugeages.formatted(cuve)));
        return id;
    }

    private ResultActions cloturer(String id) throws Exception {
        String d = lireGet(gerant, "/api/v1/journees/" + id);
        return mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(d, "$.version") + "}"));
    }

    @Test
    void encaissementsElectroniquesEtPlafondEspeces() throws Exception {
        creerStation("TRM");
        String id = journee("2026-05-01", true);

        // Encaissements : plus que les ventes, la cloture est bloquee
        String e = lireGet(gerant, "/api/v1/journees/" + id + "/encaissements");
        assertThat(JsonPath.<List<String>>read(e, "$.lignes[*].mode")).containsExactly("WAVE", "ORANGE_MONEY", "CARTE");
        e = modifier(gerant, "/api/v1/journees/" + id + "/encaissements",
                "{\"version\":%s,\"encaissements\":[{\"mode\":\"WAVE\",\"montant\":80000}]}".formatted(JsonPath.read(e, "$.version").toString()));
        cloturer(id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("supérieurs aux ventes")));

        // Wave 20 000 et carte 5 500 : especes a verser 75 500 - 25 500 = 50 000
        e = modifier(gerant, "/api/v1/journees/" + id + "/encaissements",
                "{\"version\":%s,\"encaissements\":[{\"mode\":\"WAVE\",\"montant\":20000},{\"mode\":\"CARTE\",\"montant\":5500}]}"
                        .formatted(JsonPath.read(e, "$.version").toString()));
        assertThat(nombre(e, "$.total")).isEqualByComparingTo("25500");
        String recette = lireGet(gerant, "/api/v1/journees/" + id + "/tresorerie");
        assertThat(nombre(recette, "$.encaissements")).isEqualByComparingTo("25500");
        assertThat(nombre(recette, "$.montantAVerser")).isEqualByComparingTo("50000");
        assertThat(JsonPath.<Object>read(recette, "$.plafondEspeces")).isNull();

        // Plafond de 40 000 F fixe par le controle de gestion (pas par le gerant)
        String controleur = OutilsApi.creerEtConnecter(mvc, jdbc, "CONTROLEUR_GESTION", "RESEAU", List.of());
        mvc.perform(avec(put("/api/v1/stations/" + station + "/plafond-especes"), gerant).content("{\"plafond\":40000}"))
                .andExpect(status().isForbidden());
        modifier(controleur, "/api/v1/stations/" + station + "/plafond-especes", "{\"plafond\":40000}");

        cloturer(id).andExpect(status().isOk());
        assertThat(jdbc.sql("select encaissements from tresorerie.recette_journee r join exploitation.journee j on j.id = r.journee_id "
                + "where j.uuid = ?::uuid").param(id).query(BigDecimal.class).single()).isEqualByComparingTo("25500");
        recette = lireGet(gerant, "/api/v1/journees/" + id + "/tresorerie");
        assertThat(nombre(recette, "$.soldeFin")).isEqualByComparingTo("50000");
        assertThat(nombre(recette, "$.plafondEspeces")).isEqualByComparingTo("40000");
        assertThat(JsonPath.<Boolean>read(recette, "$.depassePlafond")).isTrue();
        // Plus de saisie une fois la journee cloturee
        assertThat(JsonPath.<Boolean>read(lireGet(gerant, "/api/v1/journees/" + id + "/encaissements"), "$.modifiable")).isFalse();

        // Livre de caisse et situation : encaissements hors caisse, depassement signale
        String livre = lireGet(gerant, "/api/v1/stations/" + station + "/caisse?mois=2026-05");
        assertThat(nombre(livre, "$.totalEncaissements")).isEqualByComparingTo("25500");
        assertThat(nombre(livre, "$.totalMontants")).isEqualByComparingTo("50000");
        String situation = lireGet(controleur, "/api/v1/tresorerie/situation");
        Map<String, Object> trm = JsonPath.<List<Map<String, Object>>>read(situation, "$.stations[?(@.stationCode=='TRM')]").get(0);
        assertThat(trm.get("depassePlafond")).isEqualTo(true);
        assertThat(JsonPath.<Integer>read(situation, "$.depassementsPlafond")).isPositive();

        // Plafond retire : plus d'alerte
        modifier(controleur, "/api/v1/stations/" + station + "/plafond-especes", "{\"plafond\":null}");
        assertThat(JsonPath.<Boolean>read(lireGet(gerant, "/api/v1/journees/" + id + "/tresorerie"), "$.depassePlafond")).isFalse();
    }
}
