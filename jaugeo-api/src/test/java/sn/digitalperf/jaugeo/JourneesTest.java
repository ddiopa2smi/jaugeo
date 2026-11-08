package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

/** Sprint 2 : journees d'exploitation, report d'index, ventes valorisees, verrous. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class JourneesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String gerant(String station) throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, station)));
    }

    private String superviseur() throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());
    }

    private String ouvrir(String jeton, String station, LocalDate date) throws Exception {
        return mvc.perform(avec(post("/api/v1/stations/" + uuidStation(jdbc, station) + "/journees"), jeton)
                        .content("{\"date\":\"" + date + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String detail(String jeton, String id) throws Exception {
        return mvc.perform(avec(get("/api/v1/journees/" + id), jeton)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Saisie de toutes les lignes : arrivee = depart + ecart (sauf valeurs imposees par pistolet). */
    private static String saisie(String json, BigDecimal ecart, Map<String, String> specifiques, String lectures) {
        List<Map<String, Object>> lignes = JsonPath.read(json, "$.lignes");
        List<String> releves = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            String code = (String) l.get("pistoletCode");
            BigDecimal depart = new BigDecimal(l.get("indexDepart").toString());
            String valeurs = specifiques.getOrDefault(code, "\"indexArrivee\":" + depart.add(ecart).toPlainString());
            releves.add("{\"pistoletId\":\"" + l.get("pistoletId") + "\"," + valeurs
                    + (lectures == null ? "" : ",\"lectures\":" + lectures.replace("$DEPART", depart.toPlainString())
                    .replace("$MILIEU", depart.add(ecart.divide(BigDecimal.valueOf(3), 0, java.math.RoundingMode.DOWN)).toPlainString()))
                    + "}");
        }
        int version = JsonPath.read(json, "$.version");
        return "{\"version\":" + version + ",\"observation\":\"RAS\",\"releves\":[" + String.join(",", releves) + "]}";
    }

    private String enregistrer(String jeton, String json, String corps) throws Exception {
        return mvc.perform(avec(put("/api/v1/journees/" + JsonPath.read(json, "$.id") + "/releves"), jeton).content(corps))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString()).stripTrailingZeros().toPlainString();
    }

    private static List<String> nombres(String json, String chemin) {
        List<Object> l = JsonPath.read(json, chemin);
        return l.stream().map(o -> new BigDecimal(o.toString()).stripTrailingZeros().toPlainString()).toList();
    }

    private static String version(String json) {
        return "{\"version\":" + JsonPath.read(json, "$.version") + "}";
    }

    /** Jaugeage sans ecart de chaque cuve (obligatoire a la cloture depuis le sprint 3) ; renvoie la journee a jour. */
    private String jauger(String jeton, String journee) throws Exception {
        return OutilsApi.jaugerSansEcart(mvc, jeton, JsonPath.read(journee, "$.id"));
    }

    @Test
    void enchainementDesJourneesDeNiandane() throws Exception {
        String gerant = gerant("NIA");
        String superviseur = superviseur();
        String nia = uuidStation(jdbc, "NIA");

        mvc.perform(avec(get("/api/v1/stations/" + nia + "/journees"), gerant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prochaine.possible").value(true))
                .andExpect(jsonPath("$.journees.length()").value(0));
        mvc.perform(avec(post("/api/v1/stations/" + nia + "/journees"), gerant)
                        .content("{\"date\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(1) + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JOURNEE_FUTURE"));

        // Jour 1 : index de depart = index de mise en service (G1 : 88 916)
        String j1 = ouvrir(gerant, "NIA", LocalDate.of(2026, 1, 1));
        assertThat(nombres(j1, "$.lignes[?(@.pistoletCode=='G1')].indexDepart")).containsExactly("88916");
        assertThat(JsonPath.<String>read(j1, "$.statut")).isEqualTo("BROUILLON");
        mvc.perform(avec(post("/api/v1/stations/" + nia + "/journees"), gerant).content("{\"date\":\"2026-01-02\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JOURNEE_PRECEDENTE_OUVERTE"));
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/cloture"), gerant).content(version(j1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLOTURE_IMPOSSIBLE"));

        // Saisie : G1 vend 800,5 L dont 20 L d'essai remis en cuve ; les autres pistolets 500 L
        String apres = enregistrer(gerant, j1, saisie(j1, new BigDecimal("500"),
                Map.of("G1", "\"indexArrivee\":89716.5,\"remiseCuve\":20"), null));
        assertThat(nombres(apres, "$.lignes[?(@.pistoletCode=='G1')].sortieNette")).containsExactly("780.5");
        assertThat(nombres(apres, "$.lignes[?(@.pistoletCode=='G1')].montant")).containsExactly("530740"); // 780,5 x 680
        assertThat(JsonPath.<Integer>read(apres, "$.anomalies")).isZero();
        mvc.perform(avec(put("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/releves"), gerant).content(saisie(j1, BigDecimal.TEN, Map.of(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_OBSOLETE"));

        // Le pompiste saisit mais ne clôture pas
        String pompiste = OutilsApi.creerEtConnecter(mvc, jdbc, "POMPISTE", "STATIONS", List.of(numeroStation(jdbc, "NIA")));
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/cloture"), pompiste).content(version(apres)))
                .andExpect(status().isForbidden());

        apres = jauger(gerant, apres);
        String cloturee = mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/cloture"), gerant).content(version(apres)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.actions.saisir").value(false))
                .andReturn().getResponse().getContentAsString();
        long nbLignes = JsonPath.<List<Object>>read(j1, "$.lignes").size();
        assertThat(jdbc.sql("select count(*) from exploitation.vente v join exploitation.journee j on j.id = v.journee_id where j.uuid = ?::uuid")
                .param(JsonPath.read(j1, "$.id").toString()).query(Long.class).single()).isEqualTo(nbLignes);
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/validation"), gerant).content(version(cloturee)))
                .andExpect(status().isForbidden());

        // Renvoi par le superviseur : retour en brouillon, ventes retirees
        String renvoyee = mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/renvoi"), superviseur)
                        .content("{\"version\":" + JsonPath.read(cloturee, "$.version") + ",\"motif\":\"Vérifier l'index de G2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.motifRenvoi").value("Vérifier l'index de G2"))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.sql("select count(*) from exploitation.vente v join exploitation.journee j on j.id = v.journee_id where j.uuid = ?::uuid")
                .param(JsonPath.read(j1, "$.id").toString()).query(Long.class).single()).isZero();
        String recloturee = mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/cloture"), gerant).content(version(renvoyee)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j1, "$.id") + "/validation"), superviseur).content(version(recloturee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"));

        // Jour 2 : depart de G1 reporte depuis l'arrivee du jour 1
        String j2 = ouvrir(gerant, "NIA", LocalDate.of(2026, 1, 2));
        assertThat(nombres(j2, "$.lignes[?(@.pistoletCode=='G1')].indexDepart")).containsExactly("89716.5");
        mvc.perform(avec(get("/api/v1/stations/" + nia + "/journees"), gerant))
                .andExpect(jsonPath("$.journees[0].date").value("2026-01-02"))
                .andExpect(jsonPath("$.journees[1].statut").value("VALIDEE"))
                .andExpect(jsonPath("$.prochaine.possible").value(false));

        // Hors perimetre
        String gerantYoff = gerant("YOF");
        mvc.perform(avec(get("/api/v1/journees/" + JsonPath.read(j2, "$.id")), gerantYoff)).andExpect(status().isForbidden());
        mvc.perform(avec(get("/api/v1/stations/" + nia + "/journees"), gerantYoff)).andExpect(status().isForbidden());
    }

    @Test
    void unChangementDePrixEnCoursDeJourneeExigeUnIndexIntermediaire() throws Exception {
        String gerant = gerant("KAY");
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        Instant debutJour = aujourdhui.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant changement = debutJour.plus(6, ChronoUnit.HOURS);

        String j = ouvrir(gerant, "KAY", aujourdhui);
        mvc.perform(avec(post("/api/v1/prix/changements"), direction).content("""
                        {"debut":"%s","motif":"Test intrajournalier","stations":["%s"],
                         "prix":[{"produitCode":"ESSENCE_PIROGUE","prixUnitaire":520}]}""".formatted(changement, uuidStation(jdbc, "KAY"))))
                .andExpect(status().isCreated());

        String d = detail(gerant, JsonPath.read(j, "$.id"));
        assertThat(JsonPath.<List<String>>read(d, "$.lignes[0].lecturesAttendues")).containsExactly(changement.toString());

        // Sans index intermediaire : clôture refusée
        String sans = enregistrer(gerant, d, saisie(d, new BigDecimal("300"), Map.of(), null));
        assertThat(JsonPath.<List<String>>read(sans, "$.lignes[0].anomalies[*].code")).containsExactly("LECTURE_MANQUANTE");
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j, "$.id") + "/cloture"), gerant).content(version(sans)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLOTURE_IMPOSSIBLE"));

        // 100 L a 497 F avant 6 h, 200 L a 520 F apres
        String avec = enregistrer(gerant, sans, saisie(sans, new BigDecimal("300"), Map.of(),
                "[{\"instant\":\"" + changement + "\",\"index\":$MILIEU}]"));
        assertThat(nombres(avec, "$.lignes[0].tranches[*].litres")).containsExactly("100", "200");
        assertThat(nombre(avec, "$.lignes[0].montant")).isEqualTo("153700"); // 100 x 497 + 200 x 520
        avec = jauger(gerant, avec);
        String cloturee = mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j, "$.id") + "/cloture"), gerant).content(version(avec)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(cloturee, "$.statut")).isEqualTo("CLOTUREE");

        // Journée clôturée : un nouveau prix ne peut plus prendre effet pendant cette journée
        mvc.perform(avec(post("/api/v1/prix/changements"), direction).content("""
                        {"debut":"%s","motif":"Trop tard","stations":["%s"],
                         "prix":[{"produitCode":"ESSENCE_PIROGUE","prixUnitaire":525}]}"""
                        .formatted(debutJour.plus(18, ChronoUnit.HOURS), uuidStation(jdbc, "KAY"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JOURNEE_CLOTUREE"));
    }

    @Test
    void unChangementDeCompteurEnCoursDeJourneeEstPrisEnCompte() throws Exception {
        String gerant = gerant("YOF");
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String j = ouvrir(gerant, "YOF", LocalDate.of(2026, 3, 1));
        String pistolet = JsonPath.read(j, "$.lignes[0].pistoletId");
        String code = JsonPath.read(j, "$.lignes[0].pistoletCode");
        BigDecimal depart = new BigDecimal(JsonPath.read(j, "$.lignes[0].indexDepart").toString());

        // Totalisateur remplacé à 10 h après 100 L ; le nouveau compteur part de 0
        mvc.perform(avec(post("/api/v1/pistolets/" + pistolet + "/evenements"), admin).content("""
                        {"type":"REMPLACEMENT_TOTALISATEUR","date":"2026-03-01T10:00:00Z","indexAvant":%s,"indexApres":0,
                         "commentaire":"Totalisateur bloqué"}""".formatted(depart.add(BigDecimal.valueOf(100)).toPlainString())))
                .andExpect(status().isCreated());

        String d = detail(gerant, JsonPath.read(j, "$.id"));
        assertThat(JsonPath.<List<Object>>read(d, "$.lignes[0].ruptures")).hasSize(1);
        String saisi = enregistrer(gerant, d, saisie(d, BigDecimal.TEN, Map.of(code, "\"indexArrivee\":50"), null));
        assertThat(nombre(saisi, "$.lignes[0].sortieBrute")).isEqualTo("150");
        saisi = jauger(gerant, saisi);
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(j, "$.id") + "/cloture"), gerant).content(version(saisi)))
                .andExpect(status().isOk());

        // Journée clôturée : plus de changement de compteur daté dans cette journée
        mvc.perform(avec(post("/api/v1/pistolets/" + pistolet + "/evenements"), admin).content("""
                        {"type":"REMISE_A_ZERO","date":"2026-03-01T15:00:00Z","indexAvant":40,"indexApres":0,
                         "commentaire":"Après coup"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JOURNEE_CLOTUREE"));

        // Le lendemain repart de l'index du nouveau compteur
        String j2 = ouvrir(gerant, "YOF", LocalDate.of(2026, 3, 2));
        assertThat(nombres(j2, "$.lignes[?(@.pistoletCode=='" + code + "')].indexDepart")).containsExactly("50");
    }
}
