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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Lot 2, sprint M6 : fiche client (contrat, engins, remise au litre), bons en attente de derogation, cartes
 * prepayees, facturation, balance agee et relances. Station creee pour ce test (CM6), en janvier 2026.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class ClientsM6Test {

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

    private String creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"CM6\",\"nom\":\"Station clients M6\",\"type\":\"MIXTE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
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
                {"debut":"2025-12-01T00:00:00Z","motif":"Prix de lancement","reference":"Arrêté test M6","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755},{"produitCode":"SUPER","prixUnitaire":990}],"confirmerVariation":true}"""
                .formatted(station));
        return station;
    }

    /** Ouvre la journee et releve 100 L par pistolet ; renvoie [id journee, json credit]. */
    private String[] journee(String gerant, String station, String date) throws Exception {
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + date + "\"}");
        String id = JsonPath.read(j, "$.id");
        List<Map<String, Object>> lignes = JsonPath.read(j, "$.lignes");
        List<String> releves = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            releves.add("{\"pistoletId\":\"" + l.get("pistoletId") + "\",\"indexArrivee\":"
                    + new BigDecimal(l.get("indexDepart").toString()).add(BigDecimal.valueOf(100)).toPlainString() + "}");
        }
        mvc.perform(avec(put("/api/v1/journees/" + id + "/releves"), gerant)
                .content("{\"version\":" + JsonPath.read(j, "$.version") + ",\"releves\":[" + String.join(",", releves) + "]}"))
                .andExpect(status().isOk());
        return new String[] {id, obtenir(gerant, "/api/v1/journees/" + id + "/credit")};
    }

    private ResultActions bon(String jeton, String journee, Object version, String client, String numero, String produit,
                              String litres, String immat) throws Exception {
        return mvc.perform(avec(post("/api/v1/journees/" + journee + "/bons"), jeton).content(
                "{\"version\":%s,\"clientId\":\"%s\",\"numero\":\"%s\",\"produitId\":\"%s\",\"litres\":%s,\"immatriculation\":\"%s\"}"
                        .formatted(version, client, numero, produit, litres, immat)));
    }

    private static String id(String json, String chemin) {
        return JsonPath.<List<String>>read(json, chemin).get(0);
    }

    @Test
    void ficheBonsEnAttenteCartesFacturationEtRelances() throws Exception {
        String station = creerStation();
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "CM6")));
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());

        // M6.1 Fiche : contrat (paiement a reception de facture), engin autorise, remise de 20 F/L sur le gasoil
        String client = JsonPath.read(creer(comptable, "/api/v1/clients", """
                {"code":"M6-PECHE","nom":"Coopérative de pêche M6","recouvrement":"DIRECTION","plafond":60000,
                 "contrat":{"reference":"CT-2026-07","debut":"2025-12-01","delaiPaiement":0,"cautionMontant":500000,
                 "cautionBanque":"CBAO","email":"compta@coop-m6.sn","ninea":"0012345"}}"""), "$.id");
        String fiche = creer(comptable, "/api/v1/clients/" + client + "/engins",
                "{\"type\":\"PIROGUE\",\"immatriculation\":\"dk-pir-01\",\"libelle\":\"Pirogue Ndeye\"}");
        assertThat(JsonPath.<String>read(fiche, "$.engins[0].immatriculation")).isEqualTo("DK-PIR-01");
        mvc.perform(avec(post("/api/v1/clients/" + client + "/engins"), comptable)
                        .content("{\"type\":\"PIROGUE\",\"immatriculation\":\"DK-PIR-01\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENGIN_EXISTANT"));
        mvc.perform(avec(post("/api/v1/clients/" + client + "/engins"), gerant)
                .content("{\"type\":\"PIROGUE\",\"immatriculation\":\"X\"}")).andExpect(status().isForbidden());

        String[] j1 = journee(gerant, station, "2026-01-05");
        String id1 = j1[0];
        String credit = j1[1];
        String gasoil = id(credit, "$.produits[?(@.code=='GASOIL')].id");
        String sup = id(credit, "$.produits[?(@.code=='SUPER')].id");
        fiche = creer(comptable, "/api/v1/clients/" + client + "/remises",
                "{\"produitId\":\"%s\",\"debut\":\"2025-12-01\",\"remise\":20}".formatted(gasoil));
        assertThat(JsonPath.<Boolean>read(fiche, "$.remises[0].enVigueur")).isTrue();
        assertThat(JsonPath.<String>read(fiche, "$.client.contrat.reference")).isEqualTo("CT-2026-07");

        // Bon conforme : 40 L de gasoil a 755 - 20 = 735 F (29 400 F dus, 30 200 F sortis de la recette)
        credit = lire(bon(gerant, id1, JsonPath.read(credit, "$.version"), client, "M6-1", gasoil, "40", "DK-PIR-01")
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(credit, "$.bons[0].statut")).isEqualTo("VALIDE");
        assertThat(nombre(credit, "$.bons[0].prixUnitaire")).isEqualByComparingTo("735");
        assertThat(nombre(credit, "$.bons[0].montant")).isEqualByComparingTo("29400");
        assertThat(nombre(credit, "$.totalBonsDirection")).isEqualByComparingTo("30200");

        // M6.2 Bons hors regle enregistres en attente : engin non autorise, puis plafond depasse
        credit = lire(bon(gerant, id1, JsonPath.read(credit, "$.version"), client, "M6-2", gasoil, "10", "DK-AUTRE")
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(credit, "$.bons[1].statut")).isEqualTo("EN_ATTENTE");
        assertThat(JsonPath.<String>read(credit, "$.bons[1].motifBlocage")).contains("DK-AUTRE non autorisée");
        credit = lire(bon(gerant, id1, JsonPath.read(credit, "$.version"), client, "M6-3", gasoil, "50", "DK-PIR-01")
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(credit, "$.bons[2].motifBlocage")).contains("Plafond dépassé");
        assertThat(JsonPath.<Integer>read(credit, "$.bonsEnAttente")).isEqualTo(2);

        // La cloture attend la decision de la direction
        String j = OutilsApi.jaugerSansEcart(mvc, gerant, id1);
        mvc.perform(avec(post("/api/v1/journees/" + id1 + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(j, "$.version") + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("en attente de la dérogation")));

        String attente = obtenir(direction, "/api/v1/clients/bons-en-attente");
        String b2 = id(attente, "$[?(@.numero=='M6-2')].id");
        String b3 = id(attente, "$[?(@.numero=='M6-3')].id");
        String decision = "{\"version\":%s,\"accord\":%s,\"motif\":\"%s\"}";
        mvc.perform(avec(post("/api/v1/clients/bons/" + b2 + "/decision"), gerant)
                .content(decision.formatted(0, true, "x"))).andExpect(status().isForbidden());
        Object v2 = JsonPath.<List<Object>>read(attente, "$[?(@.numero=='M6-2')].version").get(0);
        Object v3 = JsonPath.<List<Object>>read(attente, "$[?(@.numero=='M6-3')].version").get(0);
        creerOk(direction, "/api/v1/clients/bons/" + b2 + "/decision", decision.formatted(v2, true, "Pirogue de remplacement du client"));
        attente = creerOk(direction, "/api/v1/clients/bons/" + b3 + "/decision", decision.formatted(v3, false, "Plafond non négociable"));
        assertThat(JsonPath.<List<String>>read(attente, "$[?(@.stationCode=='CM6')].numero")).isEmpty();
        credit = obtenir(gerant, "/api/v1/journees/" + id1 + "/credit");
        assertThat(JsonPath.<List<String>>read(credit, "$.bons[*].statut")).containsExactly("VALIDE", "ACCORDE", "REFUSE");
        assertThat(JsonPath.<String>read(credit, "$.bons[2].recouvrement")).isEqualTo("GERANT");
        assertThat(JsonPath.<String>read(credit, "$.bons[1].derogationMotif")).isEqualTo("Pirogue de remplacement du client");

        // M6.4 Carte prepayee : emise et rechargee par la comptabilite, debitee a la station par son QR code
        String carte = creer(comptable, "/api/v1/clients/" + client + "/cartes", "{\"porteur\":\"Moussa Fall\",\"immatriculation\":\"DK-PIR-01\"}");
        String carteId = JsonPath.read(carte, "$.carte.id");
        String numero = JsonPath.read(carte, "$.carte.numero");
        carte = creer(comptable, "/api/v1/clients/cartes/" + carteId + "/recharges",
                "{\"version\":%s,\"date\":\"2026-01-04\",\"mode\":\"VIREMENT\",\"montant\":50000,\"reference\":\"VIR 12\"}"
                        .formatted(JsonPath.read(carte, "$.carte.version").toString()));
        assertThat(nombre(carte, "$.carte.solde")).isEqualByComparingTo("50000");
        assertThat(nombre(obtenir(gerant, "/api/v1/clients/cartes/numero/JAUGEO:CARTE:" + numero), "$.carte.solde"))
                .isEqualByComparingTo("50000");
        String plein = "{\"version\":%s,\"carte\":\"%s\",\"produitId\":\"%s\",\"litres\":%s}";
        credit = obtenir(gerant, "/api/v1/journees/" + id1 + "/credit");
        credit = lire(mvc.perform(avec(post("/api/v1/journees/" + id1 + "/cartes"), gerant)
                        .content(plein.formatted(JsonPath.read(credit, "$.version"), "JAUGEO:CARTE:" + numero, sup, "20")))
                .andExpect(status().isCreated()));
        assertThat(nombre(credit, "$.cartes[0].montant")).isEqualByComparingTo("19800");
        assertThat(nombre(credit, "$.totalCartes")).isEqualByComparingTo("19800");
        mvc.perform(avec(post("/api/v1/journees/" + id1 + "/cartes"), gerant)
                        .content(plein.formatted(JsonPath.read(credit, "$.version"), numero, sup, "40")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CARTE_SOLDE_INSUFFISANT"));
        String faux = numero.substring(0, 11) + (char) ('0' + (numero.charAt(11) - '0' + 1) % 10);
        mvc.perform(avec(post("/api/v1/journees/" + id1 + "/cartes"), gerant)
                        .content(plein.formatted(JsonPath.read(credit, "$.version"), faux, sup, "1")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CARTE_NUMERO_INVALIDE"));
        assertThat(nombre(obtenir(comptable, "/api/v1/clients/cartes/" + carteId), "$.carte.solde")).isEqualByComparingTo("30200");

        // Recette : bons et cartes sortent a la valeur pompe (40 + 10 + 50 L de gasoil, 20 L de super)
        BigDecimal ventes = BigDecimal.valueOf(100 * 755 + 100 * 990);
        BigDecimal montant = ventes.subtract(BigDecimal.valueOf(50 * 755)).subtract(BigDecimal.valueOf(50 * 755))
                .subtract(BigDecimal.valueOf(19_800));
        String r = obtenir(gerant, "/api/v1/journees/" + id1 + "/tresorerie");
        assertThat(nombre(r, "$.cartes")).isEqualByComparingTo("19800");
        assertThat(nombre(r, "$.montantAVerser")).isEqualByComparingTo(montant);
        String c = lire(mvc.perform(avec(post("/api/v1/journees/" + id1 + "/cloture"), gerant)
                .content("{\"version\":" + JsonPath.read(credit, "$.version") + "}")).andExpect(status().isOk()));
        assertThat(jdbc.sql("select cartes from tresorerie.recette_journee r join exploitation.journee j on j.id = r.journee_id where j.uuid = ?::uuid")
                .param(id1).query(BigDecimal.class).single()).isEqualByComparingTo("19800");
        assertThat(JsonPath.<String>read(c, "$.statut")).isEqualTo("CLOTUREE");

        // M6.3 Facturation du 05/01 : une facture, montant net des remises, echeance a la date de facture (delai 0)
        String resultat = creer(comptable, "/api/v1/clients/factures",
                "{\"du\":\"2026-01-01\",\"au\":\"2026-01-05\",\"dateFacture\":\"2026-01-05\",\"clientId\":\"%s\"}".formatted(client));
        BigDecimal du = BigDecimal.valueOf(29_400 + 10 * 735 + 50 * 735);
        assertThat(JsonPath.<Integer>read(resultat, "$.bonsFactures")).isEqualTo(3);
        assertThat(nombre(resultat, "$.factures[0].montant")).isEqualByComparingTo(du);
        String facture = JsonPath.read(resultat, "$.factures[0].id");
        assertThat(JsonPath.<String>read(resultat, "$.factures[0].numero")).startsWith("FA2026-");
        assertThat(JsonPath.<String>read(resultat, "$.factures[0].dateEcheance")).isEqualTo("2026-01-05");
        assertThat(JsonPath.<List<Object>>read(obtenir(comptable, "/api/v1/clients/factures/" + facture), "$.lignes")).hasSize(3);
        byte[] pdf = mvc.perform(avec(get("/api/v1/clients/factures/" + facture + "/pdf"), comptable))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        // Les bons factures ne sont pas refactures
        assertThat(JsonPath.<Integer>read(creer(comptable, "/api/v1/clients/factures",
                "{\"du\":\"2026-01-01\",\"au\":\"2026-01-05\",\"dateFacture\":\"2026-01-05\",\"clientId\":\"%s\"}".formatted(client)),
                "$.bonsFactures")).isZero();

        // Echeance depassee : le bon du lendemain attend la direction
        String[] j2 = journee(gerant, station, "2026-01-06");
        credit = lire(bon(gerant, j2[0], JsonPath.read(j2[1], "$.version"), client, "M6-4", gasoil, "5", "DK-PIR-01")
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(credit, "$.bons[0].statut")).isEqualTo("EN_ATTENTE");
        assertThat(JsonPath.<String>read(credit, "$.bons[0].motifBlocage")).contains("Échéance dépassée").contains("échue le 05/01/2026");
        credit = lire(mvc.perform(avec(delete("/api/v1/journees/" + j2[0] + "/bons/" + JsonPath.read(credit, "$.bons[0].id")), gerant)
                .param("version", JsonPath.read(credit, "$.version").toString())).andExpect(status().isOk()));

        // Balance agee au 20/02 : facture de 46 jours, entierement echue ; relances de niveau 1 puis 2
        String balance = obtenir(comptable, "/api/v1/clients/balance-agee?au=2026-02-20");
        String ligne = "$.lignes[?(@.code=='M6-PECHE')].";
        assertThat(new BigDecimal(JsonPath.<List<Object>>read(balance, ligne + "jours31a60").get(0).toString())).isEqualByComparingTo(du);
        assertThat(new BigDecimal(JsonPath.<List<Object>>read(balance, ligne + "echu").get(0).toString())).isEqualByComparingTo(du);
        String relance = creer(comptable, "/api/v1/clients/" + client + "/relances", "{\"mode\":\"COURRIER\"}");
        assertThat(JsonPath.<Integer>read(relance, "$.niveau")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(creer(comptable, "/api/v1/clients/" + client + "/relances", "{\"mode\":\"TELEPHONE\"}"), "$.niveau"))
                .isEqualTo(2);
        byte[] lettre = mvc.perform(avec(get("/api/v1/clients/relances/" + JsonPath.read(relance, "$.id") + "/lettre"), comptable))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(lettre, 0, 5)).isEqualTo("%PDF-");

        // Reglement au siege : impute sur la facture, plus rien d'echu
        creer(comptable, "/api/v1/clients/" + client + "/operations",
                "{\"stationId\":\"%s\",\"type\":\"REGLEMENT\",\"mode\":\"VIREMENT\",\"date\":\"2026-01-20\",\"montant\":%s}"
                        .formatted(station, du.toPlainString()));
        fiche = obtenir(comptable, "/api/v1/clients/" + client);
        assertThat(nombre(fiche, "$.factures[0].reste")).isEqualByComparingTo("0");
        assertThat(nombre(fiche, "$.client.echu")).isEqualByComparingTo("0");
        assertThat(JsonPath.<List<Object>>read(fiche, "$.cartes")).hasSize(1);
        assertThat(JsonPath.<List<Object>>read(fiche, "$.relances")).hasSize(2);
        mvc.perform(avec(post("/api/v1/clients/" + client + "/relances"), comptable).content("{}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("RIEN_A_RELANCER"));

        // Annulation : les bons redeviennent a facturer
        String detail = obtenir(comptable, "/api/v1/clients/factures/" + facture);
        detail = creerOk(comptable, "/api/v1/clients/factures/" + facture + "/annulation",
                "{\"version\":%s,\"motif\":\"Erreur de période\"}".formatted(JsonPath.read(detail, "$.facture.version").toString()));
        assertThat(JsonPath.<String>read(detail, "$.facture.statut")).isEqualTo("ANNULEE");
        assertThat(JsonPath.<Integer>read(creer(comptable, "/api/v1/clients/factures",
                "{\"du\":\"2026-01-01\",\"au\":\"2026-01-31\",\"dateFacture\":\"2026-02-01\",\"clientId\":\"%s\"}".formatted(client)),
                "$.bonsFactures")).isEqualTo(3);

        // Carte bloquee : plus de plein
        carte = obtenir(comptable, "/api/v1/clients/cartes/" + carteId);
        mvc.perform(avec(put("/api/v1/clients/cartes/" + carteId), comptable).content(
                        "{\"porteur\":\"Moussa Fall\",\"statut\":\"BLOQUEE\",\"version\":" + JsonPath.read(carte, "$.carte.version") + "}"))
                .andExpect(status().isOk());
        credit = obtenir(gerant, "/api/v1/journees/" + j2[0] + "/credit");
        mvc.perform(avec(post("/api/v1/journees/" + j2[0] + "/cartes"), gerant)
                        .content(plein.formatted(JsonPath.read(credit, "$.version"), numero, sup, "1")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CARTE_BLOQUEE"));

        // Synthese de janvier : N = cartes + remises (40 + 10 + 50 L a 20 F), R reste nul sans arrete
        String synthese = obtenir(comptable, "/api/v1/syntheses/ventes?mois=2026-01");
        BigDecimal n = new BigDecimal(JsonPath.<List<Object>>read(synthese, "$.lignes[?(@.stationCode=='CM6')].horsCaisse").get(0).toString());
        assertThat(n).isEqualByComparingTo(BigDecimal.valueOf(19_800 + 100 * 20));
        assertThat(new BigDecimal(JsonPath.<List<Object>>read(synthese, "$.lignes[?(@.stationCode=='CM6')].ecart").get(0).toString()))
                .isEqualByComparingTo("0");
    }

    private String creerOk(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(post(url), jeton).content(corps)).andExpect(status().isOk()));
    }
}
