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
import java.time.LocalDate;
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
 * Lot 2, sprint M8 : proposition de commande, tournee citerne (commandee, chargee, en route, depotee), reception
 * rattachee a la livraison, tolerance et penalite du transporteur, note, controle de facture fournisseur. Station
 * reservee a ces tests : APR (creee ici, gasoil, bareme de 10 L/mm). Journees datees d'avant-hier et d'hier pour
 * rester dans la periode de notation.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class ApprovisionnementM8Test {

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

    private String poster(String jeton, String url, String corps) throws Exception {
        return lire(mvc.perform(avec(post(url), jeton).content(corps)).andExpect(status().isOk()));
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

    private static String version(String json) {
        return JsonPath.read(json, "$.version").toString();
    }

    /** Station gasoil : une cuve de 30 000 L (creux 500 L, reappro a 29 000 L), un pistolet, gasoil a 755 F. */
    private String[] creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"APR\",\"nom\":\"Station appro\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String cuve = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500,\"seuilReapproLitres\":29000}");
        String c1 = JsonPath.read(cuve, "$.id");
        modifier(admin, "/api/v1/cuves/" + c1 + "/bareme", """
                {"certificat":"CERT-APR-1","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}""".formatted(version(cuve)));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test M8","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755}],"confirmerVariation":true}""".formatted(station));
        return new String[] {station, c1};
    }

    /** Ouvre la journee et vend des litres sur l'unique pistolet ; renvoie le stock de la journee. */
    private String journee(String gerant, String station, LocalDate date, int litres) throws Exception {
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + date + "\"}");
        Map<String, Object> ligne = JsonPath.<List<Map<String, Object>>>read(j, "$.lignes").get(0);
        BigDecimal arrivee = new BigDecimal(ligne.get("indexDepart").toString()).add(BigDecimal.valueOf(litres));
        String id = JsonPath.read(j, "$.id");
        modifier(gerant, "/api/v1/journees/" + id + "/releves", "{\"version\":%s,\"releves\":[{\"pistoletId\":\"%s\",\"indexArrivee\":%s}]}"
                .formatted(version(j), ligne.get("pistoletId"), arrivee.toPlainString()));
        return lireGet(gerant, "/api/v1/journees/" + id + "/stock");
    }

    private String jauger(String gerant, String stock, String cuve, String jaugeages) throws Exception {
        return modifier(gerant, "/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/jaugeages",
                "{\"version\":%s,\"jaugeages\":[%s]}".formatted(version(stock), jaugeages.replace("CUVE", cuve)));
    }

    private String cloturer(String gerant, String stock) throws Exception {
        return poster(gerant, "/api/v1/journees/" + JsonPath.read(stock, "$.journeeId") + "/cloture",
                "{\"version\":" + version(stock) + "}");
    }

    private static Map<String, Object> ligneApr(String propositions) {
        return JsonPath.<List<Map<String, Object>>>read(propositions, "$.lignes[?(@.stationCode=='APR')]").get(0);
    }

    @Test
    void propositionTourneeReceptionPenaliteEtFacture() throws Exception {
        String[] ids = creerStation();
        String station = ids[0];
        String cuve = ids[1];
        String gasoil = jdbc.sql("select uuid::text from referentiel.produit where code = 'GASOIL'").query(String.class).single();
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "APR")));
        String logistique = OutilsApi.creerEtConnecter(mvc, jdbc, "LOGISTIQUE", "RESEAU", List.of());
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        LocalDate j1 = LocalDate.now().minusDays(2);
        LocalDate j2 = j1.plusDays(1);

        // Partenaires : depot principal a 2 jours, prix d'achat convenu 600 F ; transporteur tolerant 0,2 % et penalite 50 000 F
        String depot = creer(logistique, "/api/v1/appro/fournisseurs",
                "{\"nom\":\"Dépôt M8\",\"delaiLivraison\":2,\"principal\":true,\"factureA15\":false,\"actif\":true}");
        String depotId = JsonPath.read(depot, "$.id");
        depot = creer(logistique, "/api/v1/appro/fournisseurs/" + depotId + "/prix",
                "{\"produitId\":\"%s\",\"debut\":\"2026-01-01\",\"prix\":600}".formatted(gasoil));
        assertThat(JsonPath.<List<Object>>read(depot, "$.prix")).hasSize(1);
        mvc.perform(avec(post("/api/v1/appro/fournisseurs"), gerant)
                        .content("{\"nom\":\"Interdit\",\"delaiLivraison\":1}"))
                .andExpect(status().isForbidden());
        String transporteur = creer(logistique, "/api/v1/appro/transporteurs", """
                {"nom":"Transports M8","contratReference":"CT-M8","contratDebut":"2026-01-01","tolerancePct":0.2,
                 "penalite":50000,"actif":true}""");
        String transporteurId = JsonPath.read(transporteur, "$.transporteur.id");
        assertThat(nombre(transporteur, "$.transporteur.toleranceAppliquee")).isEqualByComparingTo("0.2");
        transporteur = creer(logistique, "/api/v1/appro/transporteurs/" + transporteurId + "/citernes",
                "{\"immatriculation\":\"dk-8888-m8\",\"capaciteLitres\":30000,\"actif\":true}");
        String citerneId = JsonPath.read(transporteur, "$.transporteur.citernes[0].id");
        assertThat(JsonPath.<String>read(transporteur, "$.transporteur.citernes[0].immatriculation")).isEqualTo("DK-8888-M8");

        // Jour 1 : jauge d'ouverture 10 000 L, 1 000 L vendus, jauge de cloture 9 000 L
        String s = journee(gerant, station, j1, 1000);
        s = jauger(gerant, s, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"OUVERTURE\",\"hauteurMm\":1000,\"hauteurEauMm\":0},"
                + "{\"cuveId\":\"CUVE\",\"moment\":\"CLOTURE\",\"hauteurMm\":900,\"hauteurEauMm\":0}");
        cloturer(gerant, s);

        // Proposition : 9 000 L il y a 2 jours a 1 000 L/j = 7 000 L ce jour, 5 000 L a l'arrivee (delai 2 j) ;
        // stock sous le seuil de 29 000 L : remplir a 95 % de 30 000 L = 28 500 - 5 000 = 23 000 L
        String propositions = lireGet(logistique, "/api/v1/appro/propositions");
        assertThat(JsonPath.<Integer>read(propositions, "$.delaiLivraison")).isEqualTo(2);
        Map<String, Object> apr = ligneApr(propositions);
        assertThat(new BigDecimal(apr.get("stockAujourdhui").toString())).isEqualByComparingTo("7000");
        assertThat(new BigDecimal(apr.get("stockArrivee").toString())).isEqualByComparingTo("5000");
        assertThat(new BigDecimal(apr.get("autonomieJours").toString())).isEqualByComparingTo("6.5");
        assertThat(apr.get("aCommander")).isEqualTo(true);
        assertThat(new BigDecimal(apr.get("quantiteProposee").toString())).isEqualByComparingTo("23000");
        assertThat(apr.get("motif")).isEqualTo("Stock sous le seuil de réapprovisionnement.");

        // Tournee : la citerne de 30 000 L ne porte pas 40 000 L
        String tournee = """
                {"fournisseurId":"%s","transporteurId":"%s","citerneId":"%s","chauffeur":"Moussa Ndiaye",
                 "dateChargementPrevue":"%s","dateLivraisonPrevue":"%s",
                 "livraisons":[{"stationId":"%s","produitId":"%s","quantite":%s}]}""";
        mvc.perform(avec(post("/api/v1/appro/tournees"), logistique)
                        .content(tournee.formatted(depotId, transporteurId, citerneId, j2, j2, station, gasoil, 40000)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CAPACITE_DEPASSEE"));
        String t = creer(logistique, "/api/v1/appro/tournees",
                tournee.formatted(depotId, transporteurId, citerneId, j2, j2, station, gasoil, 20000));
        String tourneeId = JsonPath.read(t, "$.id");
        String numero = JsonPath.read(t, "$.numero");
        assertThat(numero).startsWith("TO" + j2.getYear() + "-");
        assertThat(JsonPath.<String>read(t, "$.statut")).isEqualTo("COMMANDEE");
        assertThat(JsonPath.<String>read(t, "$.immatriculation")).isEqualTo("DK-8888-M8");
        String livraisonId = JsonPath.read(t, "$.lignes[0].id");

        // La livraison en cours reduit la proposition : 28 500 - 5 000 - 20 000 = 3 500, soit 3 000 L
        apr = ligneApr(lireGet(logistique, "/api/v1/appro/propositions"));
        assertThat(new BigDecimal(apr.get("enCours").toString())).isEqualByComparingTo("20000");
        assertThat(new BigDecimal(apr.get("quantiteProposee").toString())).isEqualByComparingTo("3000");

        // Chargement au depot (BL obligatoire), depart
        mvc.perform(avec(post("/api/v1/appro/tournees/" + tourneeId + "/chargement"), logistique)
                        .content("{\"version\":%s,\"date\":\"%s\",\"livraisons\":[]}".formatted(version(t), j2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BL_OBLIGATOIRE"));
        t = poster(logistique, "/api/v1/appro/tournees/" + tourneeId + "/chargement", """
                {"version":%s,"date":"%s","scelles":"SC 1-4","livraisons":[{"livraisonId":"%s","numeroBl":"bl-m8-001","quantiteBl":20000}]}"""
                .formatted(version(t), j2, livraisonId));
        assertThat(JsonPath.<String>read(t, "$.statut")).isEqualTo("CHARGEE");
        assertThat(JsonPath.<String>read(t, "$.lignes[0].numeroBl")).isEqualTo("BL-M8-001");
        t = poster(logistique, "/api/v1/appro/tournees/" + tourneeId + "/depart", "{\"version\":" + version(t) + "}");
        assertThat(JsonPath.<String>read(t, "$.statut")).isEqualTo("EN_ROUTE");
        assertThat(JsonPath.<String>read(t, "$.partiLe")).isNotNull();

        // Jour 2 : le gerant voit la livraison attendue et la receptionne ; 19 900 L recus pour 20 000 L, scelles
        // rompus : 100 L (0,5 %) au-dela de la tolerance du contrat (0,2 %), imputes au transporteur
        String s2 = journee(gerant, station, j2, 1000);
        assertThat(JsonPath.<String>read(s2, "$.livraisonsAttendues[0].id")).isEqualTo(livraisonId);
        assertThat(JsonPath.<String>read(s2, "$.livraisonsAttendues[0].numeroBl")).isEqualTo("BL-M8-001");
        assertThat(JsonPath.<String>read(s2, "$.livraisonsAttendues[0].tourneeNumero")).isEqualTo(numero);
        assertThat(JsonPath.<String>read(s2, "$.livraisonsAttendues[0].transporteur")).isEqualTo("Transports M8");
        assertThat(nombre(s2, "$.livraisonsAttendues[0].tolerancePct")).isEqualByComparingTo("0.2");
        s2 = creer(gerant, "/api/v1/journees/" + JsonPath.read(s2, "$.journeeId") + "/receptions", """
                {"version":%s,"cuveId":"%s","livraisonId":"%s","numeroBl":"BL-M8-001","fournisseur":"Dépôt M8",
                 "transporteur":"Transports M8","immatriculation":"DK-8888-M8","scelles":"SC 1-4","scellesIntacts":false,
                 "quantiteBl":20000,"quantiteRecue":19900}""".formatted(version(s2), cuve, livraisonId));
        assertThat(JsonPath.<String>read(s2, "$.receptions[0].livraisonId")).isEqualTo(livraisonId);
        assertThat(JsonPath.<String>read(s2, "$.receptions[0].tourneeNumero")).isEqualTo(numero);
        assertThat(nombre(s2, "$.receptions[0].tolerancePct")).isEqualByComparingTo("0.2");
        assertThat(JsonPath.<String>read(s2, "$.receptions[0].imputation")).isEqualTo("TRANSPORTEUR");
        assertThat(nombre(s2, "$.receptions[0].complementLitres")).isEqualByComparingTo("100");
        assertThat(JsonPath.<List<Object>>read(s2, "$.livraisonsAttendues")).isEmpty();

        // Une livraison deja receptionnee ne l'est pas deux fois
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(s2, "$.journeeId") + "/receptions"), gerant).content("""
                        {"version":%s,"cuveId":"%s","livraisonId":"%s","numeroBl":"BL-M8-002","fournisseur":"Dépôt M8",
                         "quantiteBl":1000,"quantiteRecue":1000}""".formatted(version(s2), cuve, livraisonId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIVRAISON_DEJA_RECEPTIONNEE"));

        // Tournee depotee
        t = lireGet(logistique, "/api/v1/appro/tournees/" + tourneeId);
        assertThat(JsonPath.<String>read(t, "$.statut")).isEqualTo("DEPOTEE");
        assertThat(nombre(t, "$.lignes[0].reception.manquant")).isEqualByComparingTo("100");
        assertThat(JsonPath.<Boolean>read(t, "$.lignes[0].reception.horsTolerance")).isTrue();
        assertThat(JsonPath.<List<Object>>read(lireGet(logistique, "/api/v1/appro/tournees?enCours=true"),
                "$[?(@.numero=='" + numero + "')]")).isEmpty();

        // Cloture : 9 000 + 19 900 - 1 000 = 27 900 L ; la penalite du contrat est figee
        s2 = jauger(gerant, s2, cuve, "{\"cuveId\":\"CUVE\",\"moment\":\"CLOTURE\",\"hauteurMm\":2790,\"hauteurEauMm\":0}");
        assertThat(nombre(s2, "$.cuves[0].ecartLitres")).isEqualByComparingTo("0");
        String cloture2 = cloturer(gerant, s2);
        String penalites = lireGet(logistique, "/api/v1/appro/penalites?statut=A_REGLER");
        Map<String, Object> penalite = JsonPath.<List<Map<String, Object>>>read(penalites,
                "$[?(@.tourneeNumero=='" + numero + "')]").get(0);
        assertThat(new BigDecimal(penalite.get("montant").toString())).isEqualByComparingTo("50000");
        assertThat(new BigDecimal(penalite.get("manquantLitres").toString())).isEqualByComparingTo("100");
        assertThat(new BigDecimal(penalite.get("manquantPct").toString())).isEqualByComparingTo("0.5");
        assertThat(new BigDecimal(penalite.get("tolerancePct").toString())).isEqualByComparingTo("0.2");
        assertThat(penalite.get("numeroBl")).isEqualTo("BL-M8-001");
        assertThat(penalite.get("stationCode")).isEqualTo("APR");

        // Note : a l'heure (100 %), hors tolerance (0 %), manquant 0,5 % au-dela du double de la tolerance : 2,0 / 5
        String fiche = lireGet(logistique, "/api/v1/appro/transporteurs/" + transporteurId);
        assertThat(JsonPath.<Integer>read(fiche, "$.transporteur.notation.livraisons")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(fiche, "$.transporteur.notation.horsTolerance")).isEqualTo(1);
        assertThat(nombre(fiche, "$.transporteur.notation.ponctualitePct")).isEqualByComparingTo("100");
        assertThat(nombre(fiche, "$.transporteur.notation.note")).isEqualByComparingTo("2.0");
        assertThat(nombre(fiche, "$.transporteur.penalitesARegler")).isEqualByComparingTo("50000");
        assertThat(JsonPath.<List<Object>>read(fiche, "$.livraisons")).hasSize(1);

        // Facture fournisseur : prix factures au-dessus du convenu et BL inconnu
        String facture = creer(comptable, "/api/v1/appro/factures", """
                {"fournisseurId":"%s","numero":"fa-m8-1","dateFacture":"%s","lignes":[
                 {"numeroBl":"BL-M8-001","produitId":"%s","quantite":20000,"prixUnitaire":610},
                 {"numeroBl":"BL-INCONNU","produitId":"%s","quantite":5000,"prixUnitaire":600}]}"""
                .formatted(depotId, j2, gasoil, gasoil));
        String factureId = JsonPath.read(facture, "$.facture.id");
        assertThat(JsonPath.<String>read(facture, "$.facture.statut")).isEqualTo("SAISIE");
        assertThat(nombre(facture, "$.facture.montant")).isEqualByComparingTo("15200000");   // 12 200 000 + 3 000 000
        assertThat(JsonPath.<Integer>read(facture, "$.facture.anomalies")).isEqualTo(2);
        // Manquant impute au transporteur : le fournisseur est paye sur le BL au prix convenu
        assertThat(nombre(facture, "$.lignes[0].montantAttendu")).isEqualByComparingTo("12000000");
        assertThat(nombre(facture, "$.lignes[0].prixConvenu")).isEqualByComparingTo("600");
        assertThat(JsonPath.<List<String>>read(facture, "$.lignes[0].anomalies")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(facture, "$.lignes[1].anomalies")).hasSize(1);

        // Bon a payer malgre des anomalies : motif exige ; litige motive
        mvc.perform(avec(post("/api/v1/appro/factures/" + factureId + "/decision"), comptable)
                        .content("{\"version\":%s,\"bonAPayer\":true}".formatted(JsonPath.read(facture, "$.facture.version").toString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        facture = poster(comptable, "/api/v1/appro/factures/" + factureId + "/decision",
                "{\"version\":%s,\"bonAPayer\":false,\"motif\":\"Prix et BL contestés\"}"
                        .formatted(JsonPath.read(facture, "$.facture.version").toString()));
        assertThat(JsonPath.<String>read(facture, "$.facture.statut")).isEqualTo("EN_LITIGE");

        // Avoir recu : facture corrigee, conforme, bon a payer sans motif
        facture = modifier(comptable, "/api/v1/appro/factures/" + factureId, """
                {"fournisseurId":"%s","numero":"FA-M8-1","dateFacture":"%s","version":%s,"lignes":[
                 {"numeroBl":"BL-M8-001","produitId":"%s","quantite":20000,"prixUnitaire":600}]}"""
                .formatted(depotId, j2, JsonPath.read(facture, "$.facture.version").toString(), gasoil));
        assertThat(JsonPath.<Integer>read(facture, "$.facture.anomalies")).isZero();
        assertThat(nombre(facture, "$.facture.ecart")).isEqualByComparingTo("0");
        facture = poster(comptable, "/api/v1/appro/factures/" + factureId + "/decision",
                "{\"version\":%s,\"bonAPayer\":true}".formatted(JsonPath.read(facture, "$.facture.version").toString()));
        assertThat(JsonPath.<String>read(facture, "$.facture.statut")).isEqualTo("BON_A_PAYER");

        // Un BL n'est facture qu'une fois
        mvc.perform(avec(post("/api/v1/appro/factures"), comptable).content("""
                        {"fournisseurId":"%s","numero":"FA-M8-2","dateFacture":"%s","lignes":[
                         {"numeroBl":"bl-m8-001","produitId":"%s","quantite":20000,"prixUnitaire":600}]}"""
                        .formatted(depotId, j2, gasoil)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BL_DEJA_FACTURE"));

        // Penalite reglee : la journee ne peut plus etre renvoyee
        poster(logistique, "/api/v1/appro/penalites/" + penalite.get("id") + "/reglement",
                "{\"version\":%s,\"reference\":\"Retenue facture transport 11/2026\",\"date\":\"%s\"}"
                        .formatted(penalite.get("version"), LocalDate.now()));
        String superviseur = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());
        mvc.perform(avec(post("/api/v1/journees/" + JsonPath.read(cloture2, "$.id") + "/renvoi"), superviseur)
                        .content("{\"version\":" + version(cloture2) + ",\"motif\":\"Refaire la réception\"}"))
                .andExpect(jsonPath("$.code").value("PENALITE_REGLEE"))
                .andExpect(status().isUnprocessableContent());
    }
}
