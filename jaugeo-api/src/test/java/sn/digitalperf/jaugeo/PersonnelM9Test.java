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

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Lot 2, sprint M9 : employes, planning (reconduction avec rotation), feuilles de caisse par pompiste (releve a
 * chaque changement de poste, un pompiste par pistolet), blocage de la cloture, ecarts individuels figes puis
 * retires au renvoi. Station reservee a ces tests : PM9 (creee ici, gasoil, deux pistolets). Journee datee d'hier.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class PersonnelM9Test {

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

    /** Station gasoil : une cuve de 30 000 L, deux pistolets (G1 a 1 000, G2 a 5 000), gasoil a 755 F. */
    private String creerStation() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"PM9\",\"nom\":\"Station personnel\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String cuve = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}");
        String c1 = JsonPath.read(cuve, "$.id");
        modifier(admin, "/api/v1/cuves/" + c1 + "/bareme", """
                {"certificat":"CERT-PM9-1","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}""".formatted(version(cuve)));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G2\",\"libelle\":\"Gasoil 2\",\"cuveId\":\"%s\",\"indexMiseEnService\":5000}".formatted(c1));
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test M9","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755}],"confirmerVariation":true}""".formatted(station));
        return station;
    }

    private static String feuille(String employe, String especes, String electronique, String... lignes) {
        return "{\"employeId\":\"%s\",\"especes\":%s,\"electronique\":%s,\"lignes\":[%s]}"
                .formatted(employe, especes, electronique, String.join(",", lignes));
    }

    private static String ligne(String pistolet, int indexFin) {
        return "{\"pistoletId\":\"%s\",\"indexFin\":%d}".formatted(pistolet, indexFin);
    }

    @Test
    void employesPlanningFeuillesDeCaisseEtEcarts() throws Exception {
        String station = creerStation();
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "PM9")));
        String superviseur = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());
        String logistique = OutilsApi.creerEtConnecter(mvc, jdbc, "LOGISTIQUE", "RESEAU", List.of());
        LocalDate jour = LocalDate.now().minusDays(1);
        String base = "/api/v1/personnel/stations/" + station;

        // Employes : le gerant gere son equipe, la logistique non ; matricule unique
        String awa = creer(gerant, base + "/employes",
                "{\"matricule\":\"pm9-01\",\"nom\":\"Ndiaye\",\"prenom\":\"Awa\",\"fonction\":\"POMPISTE\",\"dateEntree\":\"2026-01-05\"}");
        String awaId = JsonPath.read(awa, "$.id");
        assertThat(JsonPath.<String>read(awa, "$.matricule")).isEqualTo("PM9-01");
        String moussaId = JsonPath.read(creer(gerant, base + "/employes",
                "{\"matricule\":\"PM9-02\",\"nom\":\"Fall\",\"prenom\":\"Moussa\"}"), "$.id");
        mvc.perform(avec(post(base + "/employes"), gerant).content("{\"matricule\":\"PM9-01\",\"nom\":\"Sow\",\"prenom\":\"Ali\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATRICULE_EXISTANT"));
        mvc.perform(avec(post(base + "/employes"), logistique).content("{\"nom\":\"Sow\",\"prenom\":\"Ali\"}"))
                .andExpect(status().isForbidden());
        String personnel = lireGet(gerant, base);
        assertThat(JsonPath.<List<Object>>read(personnel, "$.employes")).hasSize(2);
        assertThat(JsonPath.<Boolean>read(personnel, "$.feuillesCaisse")).isFalse();
        assertThat(JsonPath.<Boolean>read(personnel, "$.gerer")).isTrue();
        assertThat(JsonPath.<Boolean>read(personnel, "$.reglerFeuilles")).isFalse();

        // Planning : Awa le matin, Moussa le soir ; reconduit une semaine avec rotation, les postes s'echangent
        String planning = modifier(gerant, base + "/planning", """
                {"du":"%s","creneaux":[{"date":"%s","poste":"MATIN","employeId":"%s"},{"date":"%s","poste":"SOIR","employeId":"%s"}]}"""
                .formatted(jour, jour, awaId, jour, moussaId));
        assertThat(JsonPath.<List<Object>>read(planning, "$.creneaux")).hasSize(2);
        String suivante = poster(gerant, base + "/planning/reconduction",
                "{\"du\":\"%s\",\"semaines\":1,\"rotation\":true}".formatted(jour));
        assertThat(JsonPath.<String>read(suivante, "$.du")).isEqualTo(jour.plusDays(7).toString());
        assertThat(JsonPath.<List<String>>read(suivante, "$.creneaux[?(@.employeId=='" + awaId + "')].poste")).containsExactly("SOIR");
        assertThat(JsonPath.<List<String>>read(suivante, "$.creneaux[?(@.employeId=='" + moussaId + "')].poste")).containsExactly("MATIN");

        // Journee : 600 L sur G1, 400 L sur G2, soit 755 000 F
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"" + jour + "\"}");
        String id = JsonPath.read(j, "$.id");
        List<Map<String, Object>> lignes = JsonPath.read(j, "$.lignes");
        String g1 = lignes.get(0).get("pistoletId").toString();
        String g2 = lignes.get(1).get("pistoletId").toString();
        modifier(gerant, "/api/v1/journees/" + id + "/releves", """
                {"version":%s,"releves":[{"pistoletId":"%s","indexArrivee":1600},{"pistoletId":"%s","indexArrivee":5400}]}"""
                .formatted(version(j), g1, g2));
        j = OutilsApi.jaugerSansEcart(mvc, gerant, id);
        String postes = "/api/v1/journees/" + id + "/postes";

        // Feuilles inactives : la saisie est refusee ; seul le controle (superviseur) les active
        mvc.perform(avec(put(postes + "/MATIN"), gerant).content("{\"version\":%s,\"feuilles\":[%s]}"
                        .formatted(version(j), feuille(awaId, "0", "0", ligne(g1, 1300)))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("FEUILLES_INACTIVES"));
        mvc.perform(avec(put(base + "/feuilles-caisse"), gerant).content("{\"actif\":true}")).andExpect(status().isForbidden());
        assertThat(JsonPath.<Boolean>read(modifier(superviseur, base + "/feuilles-caisse", "{\"actif\":true}"), "$.feuillesCaisse")).isTrue();

        // Sans feuille de caisse, la journee ne se cloture plus
        String vue = lireGet(gerant, postes);
        assertThat(JsonPath.<Boolean>read(vue, "$.actif")).isTrue();
        assertThat(JsonPath.<List<String>>read(vue, "$.planning[*].poste")).containsExactlyInAnyOrder("MATIN", "SOIR");
        assertThat(JsonPath.<List<String>>read(vue, "$.problemes")).singleElement().asString().startsWith("Aucune feuille de caisse");
        mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant).content("{\"version\":" + version(vue) + "}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CLOTURE_IMPOSSIBLE"));

        // Un seul pompiste par pistolet pendant un poste
        vue = lireGet(gerant, postes);
        mvc.perform(avec(put(postes + "/MATIN"), gerant).content("{\"version\":%s,\"feuilles\":[%s,%s]}".formatted(version(vue),
                        feuille(awaId, "0", "0", ligne(g1, 1300)), feuille(moussaId, "0", "0", ligne(g1, 1300)))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PISTOLET_DEJA_TENU"));

        // Matin : Awa tient G1 (300 L) et G2 (200 L) : 377 500 F dus, 375 000 F remis, manquant de 2 500 F
        vue = lireGet(gerant, postes);
        vue = modifier(gerant, postes + "/MATIN", "{\"version\":%s,\"feuilles\":[%s]}".formatted(version(vue),
                feuille(awaId, "370000", "5000", ligne(g1, 1300), ligne(g2, 5200))));
        assertThat(nombre(vue, "$.postes[0].feuilles[0].du")).isEqualByComparingTo("377500");
        assertThat(nombre(vue, "$.postes[0].feuilles[0].ecart")).isEqualByComparingTo("-2500");
        assertThat(nombre(vue, "$.postes[0].feuilles[0].lignes[0].indexDebut")).isEqualByComparingTo("1000");
        // Les postes ne totalisent pas encore la journee
        assertThat(JsonPath.<List<String>>read(vue, "$.problemes")).hasSize(2)
                .anyMatch(p -> p.startsWith("Pistolet G1 : 300 L sur les feuilles de caisse pour 600 L"));

        // Soir : Moussa reprend les deux pistolets, 377 500 F dus, 378 000 F remis, excedent de 500 F
        vue = modifier(gerant, postes + "/SOIR", "{\"version\":%s,\"observation\":\"RAS\",\"feuilles\":[%s]}".formatted(version(vue),
                feuille(moussaId, "378000", "0", ligne(g1, 1600), ligne(g2, 5400))));
        assertThat(JsonPath.<List<String>>read(vue, "$.problemes")).isEmpty();
        assertThat(nombre(vue, "$.postes[1].feuilles[0].lignes[0].indexDebut")).isEqualByComparingTo("1300");
        assertThat(nombre(vue, "$.totaux.litres")).isEqualByComparingTo("1000");
        assertThat(nombre(vue, "$.totaux.du")).isEqualByComparingTo("755000");
        assertThat(nombre(vue, "$.totaux.ecart")).isEqualByComparingTo("-2000");

        // Cloture : les ecarts sont figes et visibles par employe
        String cloturee = poster(gerant, "/api/v1/journees/" + id + "/cloture", "{\"version\":" + version(vue) + "}");
        assertThat(JsonPath.<String>read(cloturee, "$.statut")).isEqualTo("CLOTUREE");
        String mois = YearMonth.from(jour).toString();
        String ecarts = lireGet(gerant, base + "/ecarts?mois=" + mois);
        assertThat(JsonPath.<List<String>>read(ecarts, "$.lignes[*].employe")).containsExactly("Awa Ndiaye", "Moussa Fall");
        assertThat(nombre(ecarts, "$.lignes[0].manquants")).isEqualByComparingTo("2500");
        assertThat(nombre(ecarts, "$.lignes[1].excedents")).isEqualByComparingTo("500");
        assertThat(nombre(ecarts, "$.net")).isEqualByComparingTo("-2000");
        String fiche = lireGet(gerant, "/api/v1/personnel/employes/" + awaId + "?mois=" + mois);
        assertThat(JsonPath.<List<Object>>read(fiche, "$.affectations")).hasSize(1);
        assertThat(nombre(fiche, "$.feuilles[0].ecart")).isEqualByComparingTo("-2500");
        assertThat(nombre(fiche, "$.manquants")).isEqualByComparingTo("2500");
        // Journee cloturee : les feuilles ne se modifient plus
        mvc.perform(avec(delete(postes + "/SOIR?version=" + version(cloturee)), gerant)).andExpect(status().is4xxClientError());

        // Renvoi : les ecarts figes sont retires
        poster(superviseur, "/api/v1/journees/" + id + "/renvoi", "{\"version\":%s,\"motif\":\"Feuille du soir à revoir\"}".formatted(version(cloturee)));
        assertThat(JsonPath.<List<Object>>read(lireGet(gerant, base + "/ecarts?mois=" + mois), "$.lignes")).isEmpty();
        vue = lireGet(gerant, postes);
        vue = lire(mvc.perform(avec(delete(postes + "/SOIR?version=" + version(vue)), gerant)).andExpect(status().isOk()));
        assertThat(JsonPath.<List<Object>>read(vue, "$.postes")).hasSize(1);
    }
}
