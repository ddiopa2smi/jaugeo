package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 5 : clients en compte, bons a credit (direction / gerant), plafond, recouvrements, creances, synthese
 * mensuelle des ventes et recettes (R = ecarts des arretes) et exports. Station creee pour ce test (CRD), en janvier 2026.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class ClientsSynthesesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String lire(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String obtenir(String jeton, String url) throws Exception {
        return lire(mvc.perform(avec(get(url), jeton)).andExpect(status().isOk()));
    }

    private String creer(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(post(url), jeton).content(corps)).andExpect(status().isCreated()));
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString());
    }

    /** Station mixte gasoil + super, un pistolet par cuve, prix depuis decembre 2025. */
    private String creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"CRD\",\"nom\":\"Station crédit\",\"type\":\"MIXTE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String c1 = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}"), "$.id");
        String c2 = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C2\",\"produitCode\":\"SUPER\",\"capaciteLitres\":20000,\"creuxSecuriteLitres\":500}"), "$.id");
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\",\"marque\":\"Tokheim\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"S1\",\"libelle\":\"Super 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c2));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2025-12-01T00:00:00Z","motif":"Prix de lancement","reference":"Arrêté test S5","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755},{"produitCode":"SUPER","prixUnitaire":990}],"confirmerVariation":true}"""
                .formatted(station));
        return station;
    }

    private String produit(String credit, String code) {
        return JsonPath.<List<String>>read(credit, "$.produits[?(@.code=='" + code + "')].id").get(0);
    }

    private ResultActions bon(String jeton, String journee, Object version, String client, String numero, String produit,
                              String litres) throws Exception {
        return mvc.perform(avec(post("/api/v1/journees/" + journee + "/bons"), jeton).content(
                "{\"version\":%s,\"clientId\":\"%s\",\"numero\":\"%s\",\"produitId\":\"%s\",\"litres\":%s,\"immatriculation\":\"dk-1234-a\"}"
                        .formatted(version, client, numero, produit, litres)));
    }

    @Test
    void bonsCreancesEtSyntheseMensuelle() throws Exception {
        String station = creerStation();
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "CRD")));
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        String superviseur = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());

        // Clients : geres par la comptabilite
        String corpsDir = "{\"code\":\"s5-dir\",\"nom\":\"Transports Diallo\",\"recouvrement\":\"DIRECTION\"}";
        mvc.perform(avec(post("/api/v1/clients"), gerant).content(corpsDir)).andExpect(status().isForbidden());
        String dir = JsonPath.read(creer(comptable, "/api/v1/clients", corpsDir), "$.id");
        String ger = JsonPath.read(creer(comptable, "/api/v1/clients",
                "{\"code\":\"S5-GER\",\"nom\":\"Pêcheurs de Guet Ndar\",\"recouvrement\":\"GERANT\",\"plafond\":200000}"), "$.id");
        mvc.perform(avec(post("/api/v1/clients"), comptable).content(corpsDir))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_EXISTANT"));
        assertThat(JsonPath.<List<String>>read(obtenir(gerant, "/api/v1/clients"), "$[?(@.code=='S5-DIR')].recouvrement"))
                .containsExactly("DIRECTION");

        // Creance existante au demarrage du suivi
        creer(comptable, "/api/v1/clients/" + ger + "/operations",
                "{\"stationId\":\"%s\",\"type\":\"REPRISE\",\"date\":\"2025-12-31\",\"montant\":50000,\"reference\":\"Créances au 31/12/2025\"}"
                        .formatted(station));

        // Journee du 05/01 : 100 L par pistolet (gasoil 755 F, super 990 F)
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"2026-01-05\"}");
        String id = JsonPath.read(j, "$.id");
        List<Map<String, Object>> lignes = JsonPath.read(j, "$.lignes");
        List<String> releves = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            releves.add("{\"pistoletId\":\"" + l.get("pistoletId") + "\",\"indexArrivee\":"
                    + new BigDecimal(l.get("indexDepart").toString()).add(BigDecimal.valueOf(100)).toPlainString() + "}");
        }
        j = lire(mvc.perform(avec(put("/api/v1/journees/" + id + "/releves"), gerant)
                .content("{\"version\":" + JsonPath.read(j, "$.version") + ",\"releves\":[" + String.join(",", releves) + "]}"))
                .andExpect(status().isOk()));

        String credit = obtenir(gerant, "/api/v1/journees/" + id + "/credit");
        String gasoil = produit(credit, "GASOIL");
        String sup = produit(credit, "SUPER");
        assertThat(premier(credit, "$.produits[?(@.code=='GASOIL')].prixUnitaire")).isEqualByComparingTo("755");

        // Bons : 40 L gasoil pour la direction (30 200 F), 50,5 L super pour le gerant (49 995 F)
        credit = lire(bon(gerant, id, JsonPath.read(credit, "$.version"), dir, "b-001", gasoil, "40").andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(credit, "$.bons[0].numero")).isEqualTo("B-001");
        assertThat(JsonPath.<String>read(credit, "$.bons[0].immatriculation")).isEqualTo("DK-1234-A");
        credit = lire(bon(gerant, id, JsonPath.read(credit, "$.version"), ger, "B-002", sup, "50.5").andExpect(status().isCreated()));
        assertThat(nombre(credit, "$.bons[1].montant")).isEqualByComparingTo("49995");
        assertThat(nombre(credit, "$.totalBonsDirection")).isEqualByComparingTo("30200");
        assertThat(nombre(credit, "$.totalBonsGerant")).isEqualByComparingTo("49995");

        // Regle 6 : numero a usage unique, plafond (encours 99 995 F + 100 980 F > 200 000 F), client actif
        bon(gerant, id, JsonPath.read(credit, "$.version"), dir, " B-001 ", gasoil, "5")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BON_DEJA_UTILISE"));
        // Hors plafond : le bon est enregistre en attente de derogation (M6) ; retire ici
        credit = lire(bon(gerant, id, JsonPath.read(credit, "$.version"), ger, "B-003", sup, "102")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bons[2].statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.bons[2].motifBlocage").value(Matchers.containsString("disponible 100 005 F"))));
        credit = lire(mvc.perform(avec(delete("/api/v1/journees/" + id + "/bons/" + JsonPath.read(credit, "$.bons[2].id")), gerant)
                .param("version", JsonPath.read(credit, "$.version").toString())).andExpect(status().isOk()));
        String fiche = obtenir(comptable, "/api/v1/clients/" + dir);
        mvc.perform(avec(put("/api/v1/clients/" + dir), comptable).content(
                        "{\"nom\":\"Transports Diallo\",\"recouvrement\":\"DIRECTION\",\"actif\":false,\"version\":" + JsonPath.read(fiche, "$.client.version") + "}"))
                .andExpect(status().isOk());
        bon(gerant, id, JsonPath.read(credit, "$.version"), dir, "B-004", gasoil, "5")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CLIENT_INACTIF"));
        fiche = obtenir(comptable, "/api/v1/clients/" + dir);
        mvc.perform(avec(put("/api/v1/clients/" + dir), comptable).content(
                        "{\"nom\":\"Transports Diallo\",\"recouvrement\":\"DIRECTION\",\"actif\":true,\"version\":" + JsonPath.read(fiche, "$.client.version") + "}"))
                .andExpect(status().isOk());

        // Recouvrement en especes par le gerant : jamais au-dela de la creance (50 000 + 49 995 F)
        String recouvrement = "{\"version\":%s,\"clientId\":\"%s\",\"montant\":%d,\"reference\":\"Reçu 12\"}";
        mvc.perform(avec(post("/api/v1/journees/" + id + "/recouvrements"), gerant)
                        .content(recouvrement.formatted(JsonPath.read(credit, "$.version"), ger, 100_000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CREANCE_INSUFFISANTE"));
        credit = lire(mvc.perform(avec(post("/api/v1/journees/" + id + "/recouvrements"), gerant)
                        .content(recouvrement.formatted(JsonPath.read(credit, "$.version"), ger, 20_000)))
                .andExpect(status().isCreated()));
        assertThat(nombre(credit, "$.totalRecouvrements")).isEqualByComparingTo("20000");

        // Cloture : les litres sur bons ne depassent pas les sorties (40 + 70 L de gasoil pour 100 L sortis)
        credit = lire(bon(gerant, id, JsonPath.read(credit, "$.version"), dir, "B-005", gasoil, "70").andExpect(status().isCreated()));
        assertThat(JsonPath.<List<String>>read(credit, "$.anomalies")).hasSize(1);
        j = OutilsApi.jaugerSansEcart(mvc, gerant, id);
        mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(j, "$.version") + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("servis à crédit")));
        credit = obtenir(gerant, "/api/v1/journees/" + id + "/credit");
        String b5 = JsonPath.<List<String>>read(credit, "$.bons[?(@.numero=='B-005')].id").get(0);
        credit = lire(mvc.perform(avec(delete("/api/v1/journees/" + id + "/bons/" + b5), gerant).param("version", JsonPath.read(credit, "$.version").toString()))
                .andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(credit, "$.anomalies")).isEmpty();

        // Recette : ventes - bons + recouvrements
        BigDecimal ventes = BigDecimal.valueOf(100 * 755 + 100 * 990);
        BigDecimal montant = ventes.subtract(BigDecimal.valueOf(30_200 + 49_995)).add(BigDecimal.valueOf(20_000));
        String r = obtenir(gerant, "/api/v1/journees/" + id + "/tresorerie");
        assertThat(nombre(r, "$.bonsDirection")).isEqualByComparingTo("30200");
        assertThat(nombre(r, "$.bonsGerant")).isEqualByComparingTo("49995");
        assertThat(nombre(r, "$.recouvrements")).isEqualByComparingTo("20000");
        assertThat(nombre(r, "$.montantAVerser")).isEqualByComparingTo(montant);

        String c = lire(mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant)
                        .content("{\"version\":" + JsonPath.read(credit, "$.version") + "}"))
                .andExpect(status().isOk()));
        assertThat(jdbc.sql("select montant_a_verser from tresorerie.recette_journee r join exploitation.journee j on j.id = r.journee_id where j.uuid = ?::uuid")
                .param(id).query(BigDecimal.class).single()).isEqualByComparingTo(montant);
        bon(gerant, id, JsonPath.read(c, "$.version"), dir, "B-006", gasoil, "1")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("JOURNEE_CLOTUREE"));
        assertThat(JsonPath.<Boolean>read(obtenir(gerant, "/api/v1/journees/" + id + "/credit"), "$.modifiable")).isFalse();

        // Versement, puis arrete de caisse du 05/01 : 2 000 F manquent
        creer(gerant, "/api/v1/tresorerie/versements",
                "{\"stationId\":\"%s\",\"dateDepot\":\"2026-01-06\",\"banque\":\"CBAO\",\"referenceBv\":\"S5-1\",\"montant\":100000}".formatted(station));
        String compte = montant.subtract(BigDecimal.valueOf(2_000)).toPlainString();
        creer(superviseur, "/api/v1/stations/" + station + "/arretes", "{\"date\":\"2026-01-05\",\"type\":\"CONTROLE\",\"especesComptees\":"
                + compte + ",\"responsable\":\"Gérant CRD\",\"observation\":\"Comptage de fin de journée\"}");

        // Reglement de la direction au siege ; les especes de la station se saisissent dans la journee
        String reglement = "{\"stationId\":\"%s\",\"type\":\"REGLEMENT\",\"mode\":\"%s\",\"date\":\"2026-01-20\",\"montant\":%d,\"reference\":\"VIR 88\"}";
        mvc.perform(avec(post("/api/v1/clients/" + dir + "/operations"), comptable).content(reglement.formatted(station, "VIREMENT", 40_000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CREANCE_INSUFFISANTE"));
        mvc.perform(avec(post("/api/v1/clients/" + dir + "/operations"), comptable).content(reglement.formatted(station, "ESPECES_STATION", 1_000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MODE_INVALIDE"));
        fiche = creer(comptable, "/api/v1/clients/" + dir + "/operations", reglement.formatted(station, "VIREMENT", 30_200));
        assertThat(nombre(fiche, "$.client.encours")).isEqualByComparingTo("0");

        // Releve du client du gerant en janvier : 50 000 F repris + 49 995 F - 20 000 F
        String releve = obtenir(comptable, "/api/v1/clients/" + ger + "/releve?station=" + station + "&du=2026-01-01&au=2026-01-31");
        assertThat(nombre(releve, "$.soldeDebut")).isEqualByComparingTo("50000");
        assertThat(JsonPath.<List<String>>read(releve, "$.lignes[*].type")).containsExactly("BON", "REGLEMENT");
        assertThat(nombre(releve, "$.soldeFin")).isEqualByComparingTo("79995");
        fiche = obtenir(gerant, "/api/v1/clients/" + ger);
        assertThat(nombre(fiche, "$.comptes[0].creance")).isEqualByComparingTo("79995");
        assertThat(nombre(fiche, "$.client.disponible")).isEqualByComparingTo("120005");

        // Synthese de janvier : H = E + F + G, Q = I + J + K + L + M + O, R = Q - H = ecart de l'arrete
        String synthese = obtenir(comptable, "/api/v1/syntheses/ventes?mois=2026-01");
        String ligne = "$.lignes[?(@.stationCode=='CRD')].";
        BigDecimal soldeCaisse = new BigDecimal(compte).subtract(BigDecimal.valueOf(100_000));
        assertThat(premier(synthese, ligne + "recettes")).isEqualByComparingTo(ventes);
        assertThat(premier(synthese, ligne + "litres.GASOIL")).isEqualByComparingTo("100");
        assertThat(premier(synthese, ligne + "soldeOuverture")).isEqualByComparingTo("0");
        assertThat(premier(synthese, ligne + "creancesDebut")).isEqualByComparingTo("50000");
        assertThat(premier(synthese, ligne + "aVerser")).isEqualByComparingTo(ventes.add(BigDecimal.valueOf(50_000)));
        assertThat(premier(synthese, ligne + "versements")).isEqualByComparingTo("100000");
        assertThat(premier(synthese, ligne + "soldeCaisse")).isEqualByComparingTo(soldeCaisse);
        assertThat(premier(synthese, ligne + "creancesFin")).isEqualByComparingTo("79995");
        assertThat(premier(synthese, ligne + "reglementsSiege")).isEqualByComparingTo("30200");
        assertThat(premier(synthese, ligne + "ecart")).isEqualByComparingTo("-2000");
        assertThat(premier(synthese, ligne + "ecartsArretes")).isEqualByComparingTo("-2000");
        assertThat(JsonPath.<List<String>>read(synthese, ligne + "observation").get(0))
                .contains("−2 000 F (manquant)").contains("Créances à recouvrer par le gérant : 79 995 F");

        // Observation du controle de gestion
        String commentaire = "{\"stationId\":\"%s\",\"mois\":\"2026-01\",\"texte\":\"Manquant retenu sur commission\"}".formatted(station);
        mvc.perform(avec(put("/api/v1/syntheses/commentaires"), gerant).content(commentaire)).andExpect(status().isForbidden());
        mvc.perform(avec(put("/api/v1/syntheses/commentaires"), comptable).content(commentaire))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        assertThat(JsonPath.<List<String>>read(obtenir(gerant, "/api/v1/syntheses/ventes?mois=2026-01"), "$.lignes[*].stationCode"))
                .containsExactly("CRD");

        // Synthese des stocks : les deux cuves, une journee
        String stocks = obtenir(comptable, "/api/v1/syntheses/stocks?mois=2026-01");
        assertThat(JsonPath.<List<String>>read(stocks, "$.lignes[?(@.stationCode=='CRD')].cuveCode")).containsExactly("C1", "C2");
        assertThat(JsonPath.<List<Integer>>read(stocks, "$.lignes[?(@.stationCode=='CRD')].jours")).containsExactly(1, 1);

        // Exports : classeur aux formules recalculees, PDF
        byte[] xlsx = mvc.perform(avec(get("/api/v1/syntheses/export.xlsx?mois=2026-01"), comptable))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet feuille = wb.getSheet("Ventes et recettes");
            assertThat(wb.getSheet("Stocks")).isNotNull();
            Row crd = null;
            for (Row row : feuille) {
                if (row.getCell(0) != null && row.getCell(0).getStringCellValue().startsWith("CRD")) {
                    crd = row;
                }
            }
            assertThat(crd).isNotNull();
            int dernier = crd.getLastCellNum() - 1;
            assertThat(crd.getCell(dernier - 1).getNumericCellValue()).isEqualTo(-2000d);
            assertThat(crd.getCell(dernier).getStringCellValue()).startsWith("Manquant retenu sur commission");
        }
        byte[] pdf = mvc.perform(avec(get("/api/v1/syntheses/export.pdf?mois=2026-01"), gerant))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private static BigDecimal premier(String json, String chemin) {
        return new BigDecimal(JsonPath.<List<Object>>read(json, chemin).get(0).toString());
    }
}
