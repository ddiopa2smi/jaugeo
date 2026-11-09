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
import java.math.RoundingMode;
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
 * Lot 2, sprint M4 : depotage a 15 °C et imputation du manquant, eau en fond de cuve, autonomie de stock et
 * seuil de reapprovisionnement. Station reservee a ces tests : DEP (creee ici, gasoil, bareme de 10 L/mm).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class DepotageTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String lire(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String creer(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(post(url), jeton).content(corps)).andExpect(status().isCreated()));
    }

    private String modifier(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(put(url), jeton).content(corps)).andExpect(status().isOk()));
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString());
    }

    /** Station gasoil : une cuve de 30 000 L (creux 500 L, seuil d'eau 25 mm, reappro a 29 000 L), un pistolet. */
    private String[] creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"DEP\",\"nom\":\"Station dépotage\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String cuve = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500,"
                        + "\"seuilEauMm\":25,\"seuilReapproLitres\":29000}");
        assertThat(JsonPath.<Integer>read(cuve, "$.seuilEauMm")).isEqualTo(25);
        String c1 = JsonPath.read(cuve, "$.id");
        modifier(admin, "/api/v1/cuves/" + c1 + "/bareme", """
                {"certificat":"CERT-DEP-1","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}"""
                .formatted(JsonPath.read(cuve, "$.version").toString()));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test M4","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755}],"confirmerVariation":true}""".formatted(station));
        return new String[] {station, c1};
    }

    /** Ouvre la journee et vend des litres sur l'unique pistolet ; renvoie le stock de la journee. */
    private String journee(String gerant, String station, String date, int litres) throws Exception {
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + date + "\"}");
        Map<String, Object> ligne = JsonPath.<List<Map<String, Object>>>read(j, "$.lignes").get(0);
        BigDecimal arrivee = new BigDecimal(ligne.get("indexDepart").toString()).add(BigDecimal.valueOf(litres));
        String id = JsonPath.read(j, "$.id");
        modifier(gerant, "/api/v1/journees/" + id + "/releves", "{\"version\":%s,\"releves\":[{\"pistoletId\":\"%s\",\"indexArrivee\":%s}]}"
                .formatted(JsonPath.read(j, "$.version").toString(), ligne.get("pistoletId"), arrivee.toPlainString()));
        return lire(mvc.perform(avec(get("/api/v1/journees/" + id + "/stock"), gerant)).andExpect(status().isOk()));
    }

    private ResultActions jauger(String gerant, String stock, String cuve, String jaugeages) throws Exception {
        return mvc.perform(avec(put("/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/jaugeages"), gerant)
                .content("{\"version\":%s,\"jaugeages\":[%s]}".formatted(JsonPath.read(stock, "$.version").toString(),
                        jaugeages.replace("CUVE", cuve))));
    }

    private String cloturer(String gerant, String stock) throws Exception {
        return lire(mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/cloture"), gerant)
                .content("{\"version\":" + JsonPath.read(stock, "$.version") + "}")).andExpect(status().isOk()));
    }

    @Test
    void depotageA15EauEnFondDeCuveEtAutonomie() throws Exception {
        String[] ids = creerStation();
        String station = ids[0];
        String cuve = ids[1];
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "DEP")));

        // Jour 1 : jaugeage d'ouverture a 1 000 mm dont 30 mm d'eau (au-dela du seuil de 25 mm)
        String s = journee(gerant, station, "2026-03-01", 100);
        jauger(gerant, s, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"OUVERTURE\",\"hauteurMm\":1000,\"hauteurEauMm\":1000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EAU_SUPERIEURE_JAUGE"));
        s = lire(jauger(gerant, s, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"OUVERTURE\",\"hauteurMm\":1000,\"hauteurEauMm\":30}")
                .andExpect(status().isOk()));
        assertThat(nombre(s, "$.cuves[0].ouverture.volumeLitres")).isEqualByComparingTo("9700");   // 10 000 - 300 L d'eau
        assertThat(nombre(s, "$.cuves[0].ouverture.volumeEauLitres")).isEqualByComparingTo("300");
        assertThat(JsonPath.<List<String>>read(s, "$.cuves[0].alertes[*].code")).containsExactly("EAU_FOND_CUVE");
        assertThat(JsonPath.<String>read(s, "$.cuves[0].alertes[0].message")).contains("30 mm d'eau", "seuil de 25 mm");

        // Reception : densite hors table refusee
        String reception = """
                {"version":%s,"cuveId":"%s","numeroBl":"BL-15-001","fournisseur":"Dépôt SAR","transporteur":"Transports Fall",
                 "immatriculation":"dk-1515-aa","scelles":"SC 77-80","scellesIntacts":true,"quantiteBl":20000,"quantiteBl15":19850,
                 "quantiteRecue":20000,"temperatureC":30,"densite":%s}""";
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(s, "$.journeeId") + "/receptions"), gerant)
                        .content(reception.formatted(JsonPath.read(s, "$.version").toString(), cuve, "0.55")))
                .andExpect(status().isUnprocessableContent());

        // 20 000 L recus a 30 °C = 19 746 L a 15 °C pour 19 850 L au BL : manquant de 104 L (0,52 %) a 15 °C,
        // invisible a temperature ambiante ; scelles intacts : impute au fournisseur
        s = creer(gerant, "/api/v1/journees/" + JsonPath.read(s, "$.journeeId") + "/receptions",
                reception.formatted(JsonPath.read(s, "$.version").toString(), cuve, "0.840"));
        BigDecimal facteur = nombre(s, "$.receptions[0].facteur15");
        assertThat(facteur.setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("0.9873");
        BigDecimal recue15 = new BigDecimal("20000").multiply(facteur).setScale(2, RoundingMode.HALF_UP);
        assertThat(nombre(s, "$.receptions[0].quantiteRecue15")).isEqualByComparingTo(recue15);
        assertThat(JsonPath.<Boolean>read(s, "$.receptions[0].compareeA15")).isTrue();
        assertThat(JsonPath.<String>read(s, "$.receptions[0].imputation")).isEqualTo("FOURNISSEUR");
        BigDecimal manquant = new BigDecimal("19850").subtract(recue15);
        assertThat(nombre(s, "$.receptions[0].ecartLivraison")).isEqualByComparingTo(manquant.negate());
        assertThat(nombre(s, "$.receptions[0].complementLitres")).isEqualByComparingTo(manquant);
        // Le stock, lui, recoit les litres ambiants
        assertThat(nombre(s, "$.cuves[0].stockTheorique")).isEqualByComparingTo("29600");   // 9 700 + 20 000 - 100

        // Cloture : jauge 2 960 mm sans eau ; l'alerte d'eau ne bloque pas
        s = lire(jauger(gerant, s, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"CLOTURE\",\"hauteurMm\":2960,\"hauteurEauMm\":0}")
                .andExpect(status().isOk()));
        assertThat(nombre(s, "$.cuves[0].ecartLitres")).isEqualByComparingTo("0");
        assertThat(JsonPath.<Integer>read(s, "$.anomalies")).isZero();
        cloturer(gerant, s);
        Map<String, Object> complement = jdbc.sql("""
                select c.litres, c.impute_a, c.a_15, c.montant from stock.complement_citerne c
                join stock.reception r on r.id = c.reception_id where r.numero_bl = 'BL-15-001'""").query().singleRow();
        assertThat((BigDecimal) complement.get("litres")).isEqualByComparingTo(manquant);
        assertThat(complement.get("impute_a")).isEqualTo("FOURNISSEUR");
        assertThat(complement.get("a_15")).isEqualTo(true);
        assertThat((BigDecimal) complement.get("montant")).isEqualByComparingTo(manquant.multiply(new BigDecimal("755")).setScale(0, RoundingMode.HALF_UP));

        // Jour 2 : 1 000 L vendus, jauge 2 860 mm, eau a 40 mm (volume deduit)
        String s2 = journee(gerant, station, "2026-03-02", 1000);
        s2 = lire(jauger(gerant, s2, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"CLOTURE\",\"hauteurMm\":2900,\"hauteurEauMm\":40,"
                + "\"observation\":\"Eau signalée à la logistique\"}").andExpect(status().isOk()));
        assertThat(nombre(s2, "$.cuves[0].cloture.volumeLitres")).isEqualByComparingTo("28600");
        assertThat(nombre(s2, "$.cuves[0].ecartLitres")).isEqualByComparingTo("0");
        cloturer(gerant, s2);

        // Situation : ventes moyennes (100 + 1 000) / 2 = 550 L, autonomie (28 600 - 500) / 550 = 51,1 jours ;
        // stock sous le seuil de 29 000 L : a reapprovisionner ; eau au-dela du seuil
        String logistique = OutilsApi.creerEtConnecter(mvc, jdbc, "LOGISTIQUE", "RESEAU", List.of());
        String situation = lire(mvc.perform(avec(get("/api/v1/stock/situation"), logistique)).andExpect(status().isOk()));
        Map<String, Object> dep = JsonPath.<List<Map<String, Object>>>read(situation, "$.cuves[?(@.stationCode=='DEP')]").get(0);
        assertThat(new BigDecimal(dep.get("ventesMoyennes").toString())).isEqualByComparingTo("550");
        assertThat(dep.get("joursMoyenne")).isEqualTo(2);
        assertThat(new BigDecimal(dep.get("autonomieJours").toString())).isEqualByComparingTo("51.1");
        assertThat(dep.get("aReapprovisionner")).isEqualTo(true);
        assertThat(dep.get("hauteurEauMm")).isEqualTo(40);
        assertThat(dep.get("eauAlerte")).isEqualTo(true);
        assertThat(JsonPath.<Integer>read(situation, "$.cuvesAReapprovisionner")).isPositive();
        assertThat(JsonPath.<Integer>read(situation, "$.cuvesEau")).isPositive();

        // Complement impute au fournisseur, a 15 °C
        String complements = lire(mvc.perform(avec(get("/api/v1/stock/complements"), logistique)).andExpect(status().isOk()));
        Map<String, Object> c = JsonPath.<List<Map<String, Object>>>read(complements, "$[?(@.numeroBl=='BL-15-001')]").get(0);
        assertThat(c.get("imputeA")).isEqualTo("FOURNISSEUR");
        assertThat(c.get("a15")).isEqualTo(true);
    }
}
