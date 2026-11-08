package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;
import static sn.digitalperf.jaugeo.OutilsApi.uuidStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sprint 3 : jaugeages, receptions BL, stock theorique, ecarts alertes, complements citerne.
 * Stations reservees a ces tests : Hydrobase (a partir du 01/01/2026) et Mbour (a partir du 02/02/2026).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class StocksTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String gerant(String station) throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, station)));
    }

    private String role(String role) throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, role, "RESEAU", List.of());
    }

    private String ouvrir(String jeton, String station, LocalDate date) throws Exception {
        return mvc.perform(avec(post("/api/v1/stations/" + uuidStation(jdbc, station) + "/journees"), jeton)
                        .content("{\"date\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** Chaque pistolet vend le meme volume ; renvoie le detail de la journee. */
    private String vendre(String jeton, String journee, int litresParPistolet) throws Exception {
        List<Map<String, Object>> lignes = JsonPath.read(journee, "$.lignes");
        List<String> releves = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            BigDecimal depart = new BigDecimal(l.get("indexDepart").toString());
            releves.add("{\"pistoletId\":\"" + l.get("pistoletId") + "\",\"indexArrivee\":"
                    + depart.add(BigDecimal.valueOf(litresParPistolet)).toPlainString() + "}");
        }
        return mvc.perform(avec(put("/api/v1/journees/" + id(journee) + "/releves"), jeton)
                        .content("{\"version\":" + JsonPath.read(journee, "$.version") + ",\"releves\":[" + String.join(",", releves) + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String stock(String jeton, String journee) throws Exception {
        return mvc.perform(avec(get("/api/v1/journees/" + id(journee) + "/stock"), jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String jauger(String jeton, String stock, String jaugeages) throws Exception {
        return mvc.perform(avec(put("/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/jaugeages"), jeton)
                        .content("{\"version\":" + JsonPath.read(stock, "$.version") + ",\"jaugeages\":[" + jaugeages + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String jaugeage(String stock, String moment, String valeur, String observation) {
        return "{\"cuveId\":\"" + JsonPath.read(stock, "$.cuves[0].cuveId") + "\",\"moment\":\"" + moment + "\"," + valeur
                + (observation == null ? "" : ",\"observation\":\"" + observation + "\"") + "}";
    }

    private static String reception(String stock, String bl, String quantiteBl, String recue) {
        return """
                {"version":%s,"cuveId":"%s","numeroBl":"%s","fournisseur":"Dépôt SAR","transporteur":"Transports Ndiaye",
                 "chauffeur":"Moussa Diatta","immatriculation":"dk-4512-bb","scelles":"SC 1001-1004","quantiteBl":%s%s}"""
                .formatted(JsonPath.read(stock, "$.version"), JsonPath.read(stock, "$.cuves[0].cuveId"), bl, quantiteBl,
                        recue == null ? "" : ",\"quantiteRecue\":" + recue);
    }

    private String receptionner(String jeton, String stock, String corps) throws Exception {
        return mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/receptions"), jeton).content(corps))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String cloturer(String jeton, String journeeId, Object version) throws Exception {
        return mvc.perform(avec(post("/api/v1/journees/" + journeeId + "/cloture"), jeton).content("{\"version\":" + version + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }

    private static String nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString()).stripTrailingZeros().toPlainString();
    }

    @Test
    void ecartDeStockCalculeAlerteEtFigeAHydrobase() throws Exception {
        String gerant = gerant("HYD");
        String superviseur = role("SUPERVISEUR");
        String logistique = role("LOGISTIQUE");

        // Jour 1 : premiere journee suivie, 300 L par pistolet
        String j1 = vendre(gerant, ouvrir(gerant, "HYD", LocalDate.of(2026, 1, 1)), 300);
        int pistolets = JsonPath.<List<Object>>read(j1, "$.lignes").size();
        BigDecimal sorties = BigDecimal.valueOf(300L * pistolets);
        String s = stock(gerant, j1);
        assertThat(JsonPath.<Boolean>read(s, "$.cuves[0].ouvertureRequise")).isTrue();
        assertThat(JsonPath.<List<String>>read(s, "$.cuves[0].anomalies[*].code"))
                .containsExactly("STOCK_DEPART_MANQUANT", "JAUGEAGE_MANQUANT");
        assertThat(nombre(s, "$.cuves[0].sorties")).isEqualTo(sorties.toPlainString());
        mvc.perform(avec(post("/api/v1/journees/" + id(j1) + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(s, "$.version") + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLOTURE_IMPOSSIBLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Cuve C1 (Essence pirogue)")));

        // Stock de depart 10 000 L ; reception de 30 000 L au BL, 29 850 L mesures (manquant 0,5 % > 0,3 %)
        s = jauger(gerant, s, jaugeage(s, "OUVERTURE", "\"volumeLitres\":10000", null));
        s = receptionner(gerant, s, reception(s, "BL008265", "30000", "29850"));
        assertThat(JsonPath.<String>read(s, "$.receptions[0].immatriculation")).isEqualTo("DK-4512-BB");
        assertThat(nombre(s, "$.receptions[0].complementLitres")).isEqualTo("150");
        assertThat(nombre(s, "$.receptions[0].ecartLivraisonPct")).isEqualTo("-0.5");
        BigDecimal theorique = new BigDecimal("39850").subtract(sorties);
        assertThat(nombre(s, "$.cuves[0].stockTheorique")).isEqualTo(theorique.toPlainString());

        // Jauge 200 L sous le theorique : hors tolerance, observation exigee
        String physique = theorique.subtract(BigDecimal.valueOf(200)).toPlainString();
        s = jauger(gerant, s, jaugeage(s, "CLOTURE", "\"volumeLitres\":" + physique, null));
        assertThat(nombre(s, "$.cuves[0].ecartLitres")).isEqualTo("-200");
        assertThat(nombre(s, "$.cuves[0].ecartValeur")).isEqualTo("-99400");   // 200 x 497
        assertThat(JsonPath.<String>read(s, "$.cuves[0].niveau")).isEqualTo("ENQUETE");
        assertThat(JsonPath.<List<String>>read(s, "$.cuves[0].anomalies[*].code")).containsExactly("OBSERVATION_OBLIGATOIRE");
        mvc.perform(avec(post("/api/v1/journees/" + id(j1) + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(s, "$.version") + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("une observation est obligatoire")));
        s = jauger(gerant, s, jaugeage(s, "CLOTURE", "\"volumeLitres\":" + physique, "Fuite au raccord de la pompe 2"));
        assertThat(JsonPath.<Integer>read(s, "$.anomalies")).isZero();

        // La saisie du stock change la version de la journee : une saisie d'index sur l'ancienne version est refusee
        mvc.perform(avec(put("/api/v1/journees/" + id(j1) + "/releves"), gerant).content("{\"version\":" + JsonPath.read(j1, "$.version") + ",\"releves\":[]}"))
                .andExpect(status().isConflict());
        String c1 = cloturer(gerant, id(j1), JsonPath.read(s, "$.version"));
        assertThat(JsonPath.<String>read(c1, "$.statut")).isEqualTo("CLOTUREE");

        // Valeurs figees, complement citerne cree, journee verrouillee
        String fige = stock(gerant, j1);
        assertThat(JsonPath.<Boolean>read(fige, "$.modifiable")).isFalse();
        assertThat(nombre(fige, "$.cuves[0].ecartLitres")).isEqualTo("-200");
        assertThat(JsonPath.<String>read(fige, "$.cuves[0].cloture.observation")).isEqualTo("Fuite au raccord de la pompe 2");
        assertThat(JsonPath.<String>read(fige, "$.receptions[0].complementStatut")).isEqualTo("A_REGLER");
        assertThat(jdbc.sql("select montant from stock.complement_citerne c join exploitation.journee j on j.id = c.journee_id where j.uuid = ?::uuid")
                .param(id(j1)).query(BigDecimal.class).single()).isEqualByComparingTo("74550");   // 150 x 497
        mvc.perform(avec(put("/api/v1/journees/" + id(j1) + "/jaugeages"), gerant)
                        .content("{\"version\":" + JsonPath.read(c1, "$.version") + ",\"jaugeages\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JOURNEE_CLOTUREE"));

        // Jour 2 : depart reporte du jaugeage de la veille ; le meme BL ne peut pas etre receptionne deux fois
        String j2 = vendre(gerant, ouvrir(gerant, "HYD", LocalDate.of(2026, 1, 2)), 300);
        String s2 = stock(gerant, j2);
        assertThat(JsonPath.<Boolean>read(s2, "$.cuves[0].departReporte")).isTrue();
        assertThat(nombre(s2, "$.cuves[0].stockDepart")).isEqualTo(physique);
        mvc.perform(avec(put("/api/v1/journees/" + id(j2) + "/jaugeages"), gerant)
                        .content("{\"version\":" + JsonPath.read(s2, "$.version") + ",\"jaugeages\":[" + jaugeage(s2, "OUVERTURE", "\"volumeLitres\":5", null) + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPART_REPORTE"));
        mvc.perform(avec(post("/api/v1/journees/" + id(j2) + "/receptions"), gerant).content(reception(s2, "bl008265", "30000", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BL_DEJA_RECEPTIONNE"));

        // Ecart du jour -2 L, mais le cumul du mois reste hors tolerance : l'alerte persiste
        String physique2 = new BigDecimal(physique).subtract(sorties).subtract(BigDecimal.valueOf(2)).toPlainString();
        s2 = jauger(gerant, s2, jaugeage(s2, "CLOTURE", "\"volumeLitres\":" + physique2, "Suite fuite du 01/01, réparée"));
        assertThat(nombre(s2, "$.cuves[0].ecartLitres")).isEqualTo("-2");
        assertThat(nombre(s2, "$.cuves[0].cumulEcartMois")).isEqualTo("-202");
        assertThat(JsonPath.<String>read(s2, "$.cuves[0].niveau")).isEqualTo("ENQUETE");
        String c2 = cloturer(gerant, id(j2), JsonPath.read(s2, "$.version"));

        // Alerte visible du superviseur ; analyse mensuelle
        String situation = mvc.perform(avec(get("/api/v1/stock/situation"), superviseur)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(situation, "$.cuves[?(@.stationCode=='HYD')].niveau")).containsExactly("ENQUETE");
        assertThat(JsonPath.<List<String>>read(situation, "$.cuves[?(@.stationCode=='HYD')].date")).containsExactly("2026-01-02");
        assertThat(JsonPath.<Integer>read(situation, "$.complementsARegler")).isPositive();
        String mois = mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "HYD") + "/stock?mois=2026-01"), gerant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(mois, "$.lignes")).hasSize(2);
        assertThat(nombre(mois, "$.totaux[0].receptions")).isEqualTo("29850");
        assertThat(nombre(mois, "$.totaux[0].ecartLitres")).isEqualTo("-202");
        assertThat(nombre(mois, "$.totaux[0].stockPhysique")).isEqualTo(physique2);
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "HYD") + "/stock?mois=2026-13"), gerant))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "HYD") + "/stock?mois=2026-01"), gerant("YOF")))
                .andExpect(status().isForbidden());

        // Renvoi du jour 2 : ecarts figes retires
        mvc.perform(avec(post("/api/v1/journees/" + id(j2) + "/renvoi"), superviseur)
                        .content("{\"version\":" + JsonPath.read(c2, "$.version") + ",\"motif\":\"Refaire le jaugeage\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("select count(*) from stock.ecart_stock e join exploitation.journee j on j.id = e.journee_id where j.uuid = ?::uuid")
                .param(id(j2)).query(Long.class).single()).isZero();

        // Le complement se regle par la logistique, pas par le gerant
        String complements = mvc.perform(avec(get("/api/v1/stock/complements?statut=A_REGLER"), logistique))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> hyd = JsonPath.read(complements, "$[?(@.numeroBl=='BL008265')]");
        assertThat(hyd).hasSize(1);
        assertThat(hyd.get(0).get("reglable")).isEqualTo(true);
        String corps = "{\"version\":" + hyd.get(0).get("version") + ",\"reference\":\"Reçu 2026-017\",\"date\":\"2026-01-05\"}";
        mvc.perform(avec(post("/api/v1/stock/complements/" + hyd.get(0).get("id") + "/reglement"), gerant).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(avec(post("/api/v1/stock/complements/" + hyd.get(0).get("id") + "/reglement"), logistique).content(corps))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REGLE"))
                .andExpect(jsonPath("$.reglable").value(false));
    }

    @Test
    void jaugeageParBaremeStockNegatifEtComplementRegleAMbour() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String gerant = gerant("MBO");
        String cuve = jdbc.sql("select c.uuid::text from referentiel.cuve c join referentiel.station s on s.id = c.station_id "
                + "where s.code = 'MBO' and c.code = 'C1'").query(String.class).single();
        int versionCuve = jdbc.sql("select version from referentiel.cuve where uuid = ?::uuid").param(cuve).query(Integer.class).single();
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin).content("""
                        {"certificat":"CERT-MBO-1","dateCertificat":"2026-01-20","version":%d,
                         "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":1000,"volumeLitres":10000},{"hauteurMm":2000,"volumeLitres":20000}]}"""
                        .formatted(versionCuve)))
                .andExpect(status().isOk());

        String j = vendre(gerant, ouvrir(gerant, "MBO", LocalDate.of(2026, 2, 2)), 100);
        int pistolets = JsonPath.<List<Object>>read(j, "$.lignes").size();
        String s = stock(gerant, j);
        assertThat(JsonPath.<Boolean>read(s, "$.cuves[0].bareme")).isTrue();

        // Hauteurs converties par le bareme ; hors bareme ou au-dela de la capacite : refuse
        s = jauger(gerant, s, jaugeage(s, "OUVERTURE", "\"hauteurMm\":5", null));
        assertThat(nombre(s, "$.cuves[0].ouverture.volumeLitres")).isEqualTo("50");
        mvc.perform(avec(put("/api/v1/journees/" + id(j) + "/jaugeages"), gerant)
                        .content("{\"version\":" + JsonPath.read(s, "$.version") + ",\"jaugeages\":[" + jaugeage(s, "CLOTURE", "\"hauteurMm\":2100", null) + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HAUTEUR_HORS_BAREME"));
        mvc.perform(avec(put("/api/v1/journees/" + id(j) + "/jaugeages"), gerant)
                        .content("{\"version\":" + JsonPath.read(s, "$.version") + ",\"jaugeages\":[" + jaugeage(s, "CLOTURE", "\"volumeLitres\":25000", null) + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JAUGE_SUPERIEURE_CAPACITE"));

        // 50 L au depart, ventes 100 L par pistolet : theorique negatif, la reception manque
        s = jauger(gerant, s, jaugeage(s, "CLOTURE", "\"hauteurMm\":0", null));
        assertThat(JsonPath.<List<String>>read(s, "$.cuves[0].anomalies[*].code")).contains("STOCK_THEORIQUE_NEGATIF");

        // Reception d'un BL de 5 100 L, 5 000 L mesures : complement de 100 L
        s = receptionner(gerant, s, reception(s, "BL-MBO-0001", "5100", "5000"));
        String erreur = receptionner(gerant, s, reception(s, "BL-MBO-0002", "1000", null));
        String aRetirer = JsonPath.read(erreur, "$.receptions[1].id");
        s = mvc.perform(avec(delete("/api/v1/journees/" + id(j) + "/receptions/" + aRetirer + "?version=" + JsonPath.read(erreur, "$.version")), gerant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(s, "$.receptions")).hasSize(1);
        BigDecimal theorique = new BigDecimal("5050").subtract(BigDecimal.valueOf(100L * pistolets));
        assertThat(nombre(s, "$.cuves[0].stockTheorique")).isEqualTo(theorique.toPlainString());
        int hauteur = theorique.divide(BigDecimal.TEN).intValueExact();   // 10 L par mm
        s = jauger(gerant, s, jaugeage(s, "CLOTURE", "\"hauteurMm\":" + hauteur, null));
        assertThat(JsonPath.<String>read(s, "$.cuves[0].niveau")).isEqualTo("NORMAL");
        assertThat(JsonPath.<Integer>read(s, "$.cuves[0].cloture.hauteurMm")).isEqualTo(hauteur);
        String cloturee = cloturer(gerant, id(j), JsonPath.read(s, "$.version"));

        // Complement regle : la journee ne peut plus etre renvoyee
        String logistique = role("LOGISTIQUE");
        String liste = mvc.perform(avec(get("/api/v1/stock/complements"), logistique)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> mbo = JsonPath.read(liste, "$[?(@.numeroBl=='BL-MBO-0001')]");
        assertThat(new BigDecimal(mbo.get(0).get("litres").toString())).isEqualByComparingTo("100");
        mvc.perform(avec(post("/api/v1/stock/complements/" + mbo.get(0).get("id") + "/reglement"), logistique)
                        .content("{\"version\":" + mbo.get(0).get("version") + ",\"reference\":\"VIR 8841\",\"date\":\"2026-02-01\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_REGLEMENT_INVALIDE"));
        mvc.perform(avec(post("/api/v1/stock/complements/" + mbo.get(0).get("id") + "/reglement"), logistique)
                        .content("{\"version\":" + mbo.get(0).get("version") + ",\"reference\":\"VIR 8841\",\"date\":\"2026-02-03\"}"))
                .andExpect(status().isOk());
        mvc.perform(avec(post("/api/v1/journees/" + id(j) + "/renvoi"), role("SUPERVISEUR"))
                        .content("{\"version\":" + JsonPath.read(cloturee, "$.version") + ",\"motif\":\"Contrôle\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMPLEMENT_REGLE"));
    }
}
