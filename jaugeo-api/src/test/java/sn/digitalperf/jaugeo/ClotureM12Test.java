package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.versement;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
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
 * Lot 2, sprint M12 : compte d'exploitation (marge au prix d'achat convenu du jour), cloture d'un mois par station
 * (points bloquants, compte fige, mois verrouille), regularisation d'un mois clos, budget et reel contre budget,
 * exports. Station et produit reserves a ces tests : M12 et KEROM12 (aucun autre test ne fixe son prix d'achat).
 * La journee est datee du dernier jour du mois precedent : ce mois est termine et se cloture.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class ClotureM12Test {

    private static final String BASE = "/api/v1/cloture";
    private static final String STATION = "$.stations[?(@.stationCode=='M12')]";

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

    private void refuse(ResultActions r, String code) throws Exception {
        r.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value(code));
    }

    /** Premiere valeur d'un chemin filtre (JsonPath rend une liste). */
    private static BigDecimal premier(String json, String chemin) {
        return new BigDecimal(JsonPath.<List<Object>>read(json, chemin).get(0).toString());
    }

    private static <T> T un(String json, String chemin) {
        return JsonPath.<List<T>>read(json, chemin).get(0);
    }

    private static String version(String json) {
        return JsonPath.read(json, "$.version").toString();
    }

    /** Station M12 : une cuve de petrole lampant (produit du test), un pistolet a l'index 1 000, vendu 700 F. */
    private String[] creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String produit = JsonPath.read(creer(admin, "/api/v1/produits",
                "{\"code\":\"KEROM12\",\"libelle\":\"Pétrole lampant M12\",\"famille\":\"CARBURANT\",\"unite\":\"LITRE\",\"suiviJaugeage\":true,\"actif\":true}"), "$.id");
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"M12\",\"nom\":\"Station clôture\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String cuve = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"KEROM12\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}");
        String c1 = JsonPath.read(cuve, "$.id");
        modifier(admin, "/api/v1/cuves/" + c1 + "/bareme", """
                {"certificat":"CERT-M12-1","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}""".formatted(version(cuve)));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"K1\",\"libelle\":\"Pétrole 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test M12","stations":["%s"],
                 "prix":[{"produitCode":"KEROM12","prixUnitaire":700}],"confirmerVariation":true}""".formatted(station));
        return new String[] {station, produit, direction};
    }

    @Test
    void compteExploitationClotureVerrouRegularisationEtBudget() throws Exception {
        String[] ids = creerStation();
        String station = ids[0];
        String produit = ids[1];
        String direction = ids[2];
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "M12")));
        String superviseur = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        String controleur = OutilsApi.creerEtConnecter(mvc, jdbc, "CONTROLEUR_GESTION", "RESEAU", List.of());
        String logistique = OutilsApi.creerEtConnecter(mvc, jdbc, "LOGISTIQUE", "RESEAU", List.of());
        LocalDate jour = LocalDate.now().withDayOfMonth(1).minusDays(1);
        YearMonth mois = YearMonth.from(jour);
        YearMonth courant = mois.plusMonths(1);

        // Journee du dernier jour du mois : 500 L a 700 F, soit 350 000 F ; un versement saisi le meme jour
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + jour + "\"}");
        String journee = JsonPath.read(j, "$.id");
        String pistolet = JsonPath.<List<Map<String, Object>>>read(j, "$.lignes").get(0).get("pistoletId").toString();
        modifier(gerant, "/api/v1/journees/" + journee + "/releves",
                "{\"version\":%s,\"releves\":[{\"pistoletId\":\"%s\",\"indexArrivee\":1500}]}".formatted(version(j), pistolet));
        j = OutilsApi.jaugerSansEcart(mvc, gerant, journee);
        j = poster(gerant, "/api/v1/journees/" + journee + "/cloture", "{\"version\":" + version(j) + "}");
        String saisieVersement = "{\"stationId\":\"%s\",\"dateDepot\":\"%s\",\"banque\":\"CBAO\",\"referenceBv\":\"%s\",\"montant\":100000,\"deposant\":\"Gérant M12\"}";
        String v = lire(mvc.perform(versement(gerant, saisieVersement.formatted(station, jour, "BV-M12-1"))).andExpect(status().isCreated()));

        // Le compte d'exploitation (marge) n'est pas ouvert aux gerants ni a la logistique
        mvc.perform(avec(get(BASE + "/compte?mois=" + mois), gerant)).andExpect(status().isForbidden());
        mvc.perform(avec(get(BASE + "/etat?mois=" + mois), logistique)).andExpect(status().isForbidden());

        // Sans prix d'achat convenu, le cout est inconnu : la marge n'est pas inventee
        String compte = lireGet(controleur, BASE + "/compte?mois=" + mois);
        assertThat(premier(compte, STATION + ".chiffreAffaires")).isEqualByComparingTo("350000");
        assertThat(ClotureM12Test.<Boolean>un(compte, STATION + ".coutComplet")).isFalse();
        assertThat(ClotureM12Test.<List<String>>un(compte, STATION + ".prixManquants")).containsExactly("Pétrole lampant M12");
        assertThat(ClotureM12Test.<Object>un(compte, STATION + ".produits[0].marge")).isNull();
        assertThat(JsonPath.<Boolean>read(compte, "$.total.coutComplet")).isFalse();

        // Prix d'achat convenu a 600 F : cout 300 000 F, marge 50 000 F (14,29 %)
        String depot = JsonPath.read(creer(logistique, "/api/v1/appro/fournisseurs",
                "{\"nom\":\"Dépôt M12\",\"delaiLivraison\":1,\"principal\":false,\"factureA15\":false,\"actif\":true}"), "$.id");
        creer(logistique, "/api/v1/appro/fournisseurs/" + depot + "/prix",
                "{\"produitId\":\"%s\",\"debut\":\"2026-01-01\",\"prix\":600}".formatted(produit));
        compte = lireGet(controleur, BASE + "/compte?mois=" + mois);
        assertThat(premier(compte, STATION + ".produits[0].litres")).isEqualByComparingTo("500");
        assertThat(premier(compte, STATION + ".coutAchat")).isEqualByComparingTo("300000");
        assertThat(premier(compte, STATION + ".margeCarburants")).isEqualByComparingTo("50000");
        assertThat(premier(compte, STATION + ".margeBrute")).isEqualByComparingTo("50000");
        assertThat(premier(compte, STATION + ".tauxMarge")).isEqualByComparingTo("14.29");
        assertThat(premier(compte, STATION + ".resultat")).isEqualByComparingTo("50000");
        assertThat(ClotureM12Test.<Boolean>un(compte, STATION + ".coutComplet")).isTrue();
        assertThat(ClotureM12Test.<Boolean>un(compte, STATION + ".clos")).isFalse();

        // Cloture bloquee : la journee attend sa validation et le versement sa verification
        String etat = lireGet(controleur, BASE + "/etat?mois=" + mois);
        assertThat(ClotureM12Test.<String>un(etat, STATION + ".statut")).isEqualTo("BLOQUE");
        assertThat(ClotureM12Test.<List<String>>un(etat, STATION + ".blocages")).hasSize(2)
                .anyMatch(b -> b.startsWith("1 journée à clôturer ou à valider"))
                .anyMatch(b -> b.startsWith("1 versement du mois à vérifier"));
        refuse(mvc.perform(avec(post(BASE + "/stations/" + station + "/mois/" + mois), controleur)), "CLOTURE_MOIS_IMPOSSIBLE");
        j = poster(superviseur, "/api/v1/journees/" + journee + "/validation", "{\"version\":" + version(j) + "}");
        poster(comptable, "/api/v1/tresorerie/versements/" + JsonPath.read(v, "$.id") + "/verification",
                "{\"version\":%s,\"valide\":false,\"motif\":\"Bordereau illisible\"}".formatted(version(v)));
        etat = lireGet(controleur, BASE + "/etat?mois=" + mois);
        assertThat(ClotureM12Test.<String>un(etat, STATION + ".statut")).isEqualTo("PRET");
        assertThat(ClotureM12Test.<Integer>un(etat, STATION + ".journeesValidees")).isEqualTo(1);

        // Cloture : reservee au controle de gestion, a la comptabilite et a la Direction ; elle ne se refait pas
        mvc.perform(avec(post(BASE + "/stations/" + station + "/mois/" + mois), superviseur)).andExpect(status().isForbidden());
        String clos = poster(controleur, BASE + "/stations/" + station + "/mois/" + mois, "");
        assertThat(JsonPath.<String>read(clos, "$.statut")).isEqualTo("CLOS");
        assertThat(JsonPath.<String>read(clos, "$.cloturePar")).startsWith("controleur_gestion.");
        mvc.perform(avec(post(BASE + "/stations/" + station + "/mois/" + mois), direction))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOIS_DEJA_CLOS"));

        // Mois verrouille : ni renvoi de journee, ni versement, ni arrete de caisse, ni operation de compte client
        refuse(mvc.perform(avec(post("/api/v1/journees/" + journee + "/renvoi"), superviseur)
                .content("{\"version\":%s,\"motif\":\"Index à revoir\"}".formatted(version(j)))), "MOIS_CLOS");
        refuse(mvc.perform(versement(gerant, saisieVersement.formatted(station, jour, "BV-M12-2"))), "MOIS_CLOS");
        refuse(mvc.perform(avec(post("/api/v1/stations/" + station + "/arretes"), superviseur)
                .content("{\"date\":\"" + jour + "\",\"type\":\"CONTROLE\",\"especesComptees\":350000}")), "MOIS_CLOS");
        String client = JsonPath.read(creer(comptable, "/api/v1/clients",
                "{\"code\":\"M12-CLI\",\"nom\":\"Client clôture\",\"recouvrement\":\"DIRECTION\"}"), "$.id");
        refuse(mvc.perform(avec(post("/api/v1/clients/" + client + "/operations"), comptable)
                .content("{\"stationId\":\"%s\",\"type\":\"REPRISE\",\"date\":\"%s\",\"montant\":10000,\"reference\":\"Reprise\"}"
                        .formatted(station, jour))), "MOIS_CLOS");
        // Le mois suivant reste ouvert : la journee du lendemain s'ouvre
        creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + jour.plusDays(1) + "\"}");

        // Compte fige : un prix d'achat modifie apres coup ne change plus le mois clos
        creer(logistique, "/api/v1/appro/fournisseurs/" + depot + "/prix",
                "{\"produitId\":\"%s\",\"debut\":\"%s\",\"prix\":650}".formatted(produit, jour));
        compte = lireGet(direction, BASE + "/compte?mois=" + mois);
        assertThat(premier(compte, STATION + ".coutAchat")).isEqualByComparingTo("300000");
        assertThat(premier(compte, STATION + ".resultat")).isEqualByComparingTo("50000");
        assertThat(ClotureM12Test.<Boolean>un(compte, STATION + ".clos")).isTrue();

        // Regularisation : elle vise un mois clos et s'ajoute au resultat du mois en cours
        String regul = "{\"stationId\":\"%s\",\"moisConcerne\":\"%s\",\"poste\":\"%s\",\"montant\":%d,\"motif\":\"%s\"}";
        refuse(mvc.perform(avec(post(BASE + "/regularisations"), comptable)
                .content(regul.formatted(station, courant, "DEPENSES", -15000, "Facture reçue en retard"))), "MOIS_OUVERT");
        refuse(mvc.perform(avec(post(BASE + "/regularisations"), comptable)
                .content(regul.formatted(station, mois, "INCONNU", -15000, "Facture reçue en retard"))), "POSTE_INVALIDE");
        mvc.perform(avec(post(BASE + "/regularisations"), gerant)
                .content(regul.formatted(station, mois, "DEPENSES", -15000, "Facture reçue en retard"))).andExpect(status().isForbidden());
        String r = creer(comptable, BASE + "/regularisations",
                regul.formatted(station, mois, "depenses", -15000, "Facture d'électricité reçue après la clôture"));
        assertThat(JsonPath.<String>read(r, "$.moisConcerne")).isEqualTo(mois.toString());
        assertThat(JsonPath.<String>read(r, "$.moisComptable")).isEqualTo(courant.toString());
        assertThat(JsonPath.<String>read(r, "$.poste")).isEqualTo("DEPENSES");
        String liste = lireGet(controleur, BASE + "/regularisations?annee=" + courant.getYear());
        assertThat(JsonPath.<List<String>>read(liste, "$[?(@.stationCode=='M12')].motif"))
                .containsExactly("Facture d'électricité reçue après la clôture");
        // Le mois clos ne bouge pas ; le mois en cours porte la regularisation
        assertThat(premier(lireGet(controleur, BASE + "/compte?mois=" + mois), STATION + ".resultat")).isEqualByComparingTo("50000");
        String enCours = lireGet(controleur, BASE + "/compte?mois=" + courant);
        assertThat(premier(enCours, STATION + ".regularisations")).isEqualByComparingTo("-15000");
        assertThat(premier(enCours, STATION + ".resultat")).isEqualByComparingTo("-15000");

        // Budget : 1 000 L et 700 000 F par mois, 20 000 F de depenses ; le reel du mois clos s'y compare
        String douze = String.join(",", Collections.nCopies(12, "{\"litres\":1000,\"montant\":700000}"));
        String budget = "{\"stationId\":\"%s\",\"annee\":%d,\"produits\":[{\"produitId\":\"%s\",\"mois\":[%s]}],\"depenses\":[%s]}"
                .formatted(station, mois.getYear(), produit, douze, String.join(",", Collections.nCopies(12, "20000")));
        mvc.perform(avec(put(BASE + "/budget"), gerant).content(budget)).andExpect(status().isForbidden());
        String enregistre = modifier(controleur, BASE + "/budget", budget);
        assertThat(JsonPath.<String>read(enregistre, "$.produits[0].code")).isEqualTo("KEROM12");
        assertThat(new BigDecimal(JsonPath.read(enregistre, "$.produits[0].mois[11].litres").toString())).isEqualByComparingTo("1000");
        assertThat(new BigDecimal(JsonPath.read(enregistre, "$.depenses[0]").toString())).isEqualByComparingTo("20000");
        refuse(mvc.perform(avec(put(BASE + "/budget"), controleur).content(budget.replace(douze, "{\"litres\":1,\"montant\":1}"))),
                "BUDGET_INCOMPLET");
        String suivi = lireGet(controleur, BASE + "/budget/suivi?station=" + station + "&annee=" + mois.getYear());
        String ligne = "$.mois[" + (mois.getMonthValue() - 1) + "]";
        assertThat(JsonPath.<Boolean>read(suivi, ligne + ".ecoule")).isTrue();
        assertThat(new BigDecimal(JsonPath.read(suivi, ligne + ".litresBudget").toString())).isEqualByComparingTo("1000");
        assertThat(new BigDecimal(JsonPath.read(suivi, ligne + ".litresReel").toString())).isEqualByComparingTo("500");
        assertThat(new BigDecimal(JsonPath.read(suivi, ligne + ".caReel").toString())).isEqualByComparingTo("350000");
        assertThat(new BigDecimal(JsonPath.read(suivi, ligne + ".depensesBudget").toString())).isEqualByComparingTo("20000");
        assertThat(new BigDecimal(JsonPath.read(suivi, "$.cumul.caReel").toString())).isEqualByComparingTo("350000");
        assertThat(JsonPath.<List<String>>read(suivi, "$.produits[*].code")).containsExactly("KEROM12");

        // Exports du compte d'exploitation
        byte[] xlsx = mvc.perform(avec(get(BASE + "/export.xlsx?mois=" + mois), controleur)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        byte[] pdf = mvc.perform(avec(get(BASE + "/export.pdf?mois=" + mois), controleur)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
