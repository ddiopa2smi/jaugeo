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
 * Lot 2, sprint M4 : lubrifiants et articles boutique. Entrees annoncees par la logistique et confirmees par le
 * gerant (CMUP), ventes saisies dans la journee et sorties du stock a la cloture, montant a verser, inventaire
 * tournant, renvoi. Station reservee a ces tests : ART (creee ici, gasoil, bareme de 10 L/mm).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class ArticlesBoutiqueTest {

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

    private String lireGet(String jeton, String url) throws Exception {
        return lire(mvc.perform(avec(get(url), jeton)).andExpect(status().isOk()));
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString());
    }

    private static Map<String, Object> article(String json, String chemin, String code) {
        return JsonPath.<List<Map<String, Object>>>read(json, chemin + "[?(@.code=='" + code + "')]").get(0);
    }

    @Test
    void entreesVentesInventaireEtMontantAVerser() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String station = JsonPath.read(creer(admin, "/api/v1/stations",
                "{\"code\":\"ART\",\"nom\":\"Station boutique\",\"type\":\"ROUTIERE\",\"regionCode\":\"DK\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"), "$.id");
        String cuve = creer(admin, "/api/v1/stations/" + station + "/cuves",
                "{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}");
        String c1 = JsonPath.read(cuve, "$.id");
        modifier(admin, "/api/v1/cuves/" + c1 + "/bareme", """
                {"certificat":"CERT-ART-1","dateCertificat":"2026-01-10","version":%s,
                 "points":[{"hauteurMm":0,"volumeLitres":0},{"hauteurMm":3000,"volumeLitres":30000}]}"""
                .formatted(JsonPath.read(cuve, "$.version").toString()));
        String pompe = JsonPath.read(creer(admin, "/api/v1/stations/" + station + "/pompes", "{\"code\":\"P1\"}"), "$.id");
        creer(admin, "/api/v1/pompes/" + pompe + "/pistolets",
                "{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1000}".formatted(c1));
        String huile = JsonPath.read(creer(admin, "/api/v1/produits",
                "{\"code\":\"HUILE_ART5\",\"libelle\":\"Huile moteur 5 L (test)\",\"famille\":\"LUBRIFIANT\",\"unite\":\"UNITE\",\"actif\":true}"), "$.id");
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        creer(direction, "/api/v1/prix/changements", """
                {"debut":"2026-02-01T00:00:00Z","motif":"Prix","reference":"Arrêté test boutique","stations":["%s"],
                 "prix":[{"produitCode":"GASOIL","prixUnitaire":755},{"produitCode":"HUILE_ART5","prixUnitaire":4500}],
                 "confirmerVariation":true}""".formatted(station));

        String logistique = OutilsApi.creerEtConnecter(mvc, jdbc, "LOGISTIQUE", "RESEAU", List.of());
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "ART")));
        String entree = "{\"produitId\":\"%s\",\"quantite\":%s,\"coutUnitaire\":%s,\"fournisseur\":\"Total Lubrifiants\",\"reference\":\"%s\"}";

        // Le gerant n'annonce pas les entrees : c'est la logistique
        mvc.perform(avec(post("/api/v1/stations/" + station + "/articles/entrees"), gerant)
                .content(entree.formatted(huile, 24, 3500, "BLA-1"))).andExpect(status().isForbidden());
        String e1 = creer(logistique, "/api/v1/stations/" + station + "/articles/entrees", entree.formatted(huile, 24, 3500, "BLA-1"));
        assertThat(JsonPath.<String>read(e1, "$.statut")).isEqualTo("ANNONCEE");
        String stock = lireGet(gerant, "/api/v1/stations/" + station + "/articles");
        assertThat(JsonPath.<List<Object>>read(stock, "$.entreesAnnoncees")).hasSize(1);
        assertThat(nombre(stock, "$.entreesAnnoncees[0].valeur")).isEqualByComparingTo("84000");

        // Confirmation par le gerant : 23 recus sur 24, raison exigee
        String reception = "{\"version\":%s,\"quantiteRecue\":%s,\"observation\":%s}";
        mvc.perform(avec(post("/api/v1/articles/entrees/" + JsonPath.read(e1, "$.id") + "/reception"), gerant)
                        .content(reception.formatted(JsonPath.read(e1, "$.version").toString(), 23, "null")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OBSERVATION_OBLIGATOIRE"));
        String recue = lire(mvc.perform(avec(post("/api/v1/articles/entrees/" + JsonPath.read(e1, "$.id") + "/reception"), gerant)
                        .content(reception.formatted(JsonPath.read(e1, "$.version").toString(), 23, "\"Un bidon percé\"")))
                .andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(recue, "$.statut")).isEqualTo("RECUE");
        // Deja recue : ni nouvelle reception ni annulation
        mvc.perform(avec(post("/api/v1/articles/entrees/" + JsonPath.read(e1, "$.id") + "/annulation"), logistique)
                        .content("{\"version\":" + JsonPath.read(recue, "$.version") + ",\"motif\":\"Erreur\"}"))
                .andExpect(status().isUnprocessableContent());

        // Deuxieme entree, recue en totalite : CMUP = (23 x 3 500 + 10 x 3 900) / 33 = 3 621,21
        String e2 = creer(logistique, "/api/v1/stations/" + station + "/articles/entrees", entree.formatted(huile, 10, 3900, "BLA-2"));
        mvc.perform(avec(post("/api/v1/articles/entrees/" + JsonPath.read(e2, "$.id") + "/reception"), gerant)
                .content(reception.formatted(JsonPath.read(e2, "$.version").toString(), 10, "null"))).andExpect(status().isOk());
        // Une entree annoncee par erreur s'annule avec un motif
        String e3 = creer(logistique, "/api/v1/stations/" + station + "/articles/entrees", entree.formatted(huile, 5, 3900, "BLA-3"));
        mvc.perform(avec(post("/api/v1/articles/entrees/" + JsonPath.read(e3, "$.id") + "/annulation"), logistique)
                        .content("{\"version\":" + JsonPath.read(e3, "$.version") + ",\"motif\":\"Doublon\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULEE"));
        stock = lireGet(gerant, "/api/v1/stations/" + station + "/articles");
        Map<String, Object> a = article(stock, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(a.get("quantite").toString())).isEqualByComparingTo("33");
        assertThat(new BigDecimal(a.get("cmup").toString())).isEqualByComparingTo("3621.21");
        assertThat(new BigDecimal(a.get("prixVente").toString())).isEqualByComparingTo("4500");
        assertThat(a.get("aCompter")).isEqualTo(true);   // jamais compte
        assertThat(JsonPath.<List<Object>>read(stock, "$.entreesAnnoncees")).isEmpty();

        // Journee : 100 L de gasoil, jaugeages sans ecart
        String j = creer(gerant, "/api/v1/stations/" + station + "/journees", "{\"date\":\"2026-04-01\"}");
        String jid = JsonPath.read(j, "$.id");
        Map<String, Object> ligne = JsonPath.<List<Map<String, Object>>>read(j, "$.lignes").get(0);
        j = modifier(gerant, "/api/v1/journees/" + jid + "/releves", "{\"version\":%s,\"releves\":[{\"pistoletId\":\"%s\",\"indexArrivee\":%s}]}"
                .formatted(JsonPath.read(j, "$.version").toString(), ligne.get("pistoletId"),
                        new BigDecimal(ligne.get("indexDepart").toString()).add(BigDecimal.valueOf(100)).toPlainString()));
        String s = modifier(gerant, "/api/v1/journees/" + jid + "/jaugeages", """
                {"version":%s,"jaugeages":[{"cuveId":"%s","moment":"OUVERTURE","hauteurMm":1000},
                 {"cuveId":"%s","moment":"CLOTURE","hauteurMm":990}]}""".formatted(JsonPath.read(j, "$.version").toString(), c1, c1));

        // Ventes : au-dela du stock, quantite fractionnaire refusees ; 5 bidons a 4 500 F
        String ventes = "{\"version\":%s,\"ventes\":[{\"produitId\":\"%s\",\"quantite\":%s}]}";
        String version = JsonPath.read(s, "$.version").toString();
        mvc.perform(avec(put("/api/v1/journees/" + jid + "/articles"), gerant).content(ventes.formatted(version, huile, 40)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("STOCK_ARTICLE_INSUFFISANT"));
        mvc.perform(avec(put("/api/v1/journees/" + jid + "/articles"), gerant).content(ventes.formatted(version, huile, 2.5)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("QUANTITE_ENTIERE"));
        String v = modifier(gerant, "/api/v1/journees/" + jid + "/articles", ventes.formatted(version, huile, 5));
        Map<String, Object> vente = article(v, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(vente.get("montant").toString())).isEqualByComparingTo("22500");
        assertThat(nombre(v, "$.total")).isEqualByComparingTo("22500");
        assertThat(JsonPath.<Boolean>read(v, "$.modifiable")).isTrue();

        // Montant a verser : 100 L x 755 + 22 500 = 98 000
        String recette = lireGet(gerant, "/api/v1/journees/" + jid + "/tresorerie");
        assertThat(nombre(recette, "$.ventesArticles")).isEqualByComparingTo("22500");
        assertThat(nombre(recette, "$.montantAVerser")).isEqualByComparingTo("98000");

        // Inventaire pendant la journee : 33 en stock dont 5 vendus non clotures, 27 comptes pour 28 attendus
        stock = lireGet(gerant, "/api/v1/stations/" + station + "/articles");
        a = article(stock, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(a.get("ventesEnCours").toString())).isEqualByComparingTo("5");
        assertThat(new BigDecimal(a.get("disponible").toString())).isEqualByComparingTo("28");
        String comptage = "{\"comptages\":[{\"produitId\":\"%s\",\"compte\":27,\"observation\":%s}]}";
        mvc.perform(avec(post("/api/v1/stations/" + station + "/articles/comptages"), gerant).content(comptage.formatted(huile, "null")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OBSERVATION_OBLIGATOIRE"));
        stock = lire(mvc.perform(avec(post("/api/v1/stations/" + station + "/articles/comptages"), gerant)
                .content(comptage.formatted(huile, "\"Un bidon manquant\""))).andExpect(status().isOk()));
        a = article(stock, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(a.get("quantite").toString())).isEqualByComparingTo("32");
        assertThat(a.get("aCompter")).isEqualTo(false);
        assertThat(a.get("dernierComptage")).isNotNull();
        assertThat(jdbc.sql("select ecart_valeur from stock.comptage_article c join referentiel.produit p on p.id = c.produit_id "
                + "where p.code = 'HUILE_ART5'").query(BigDecimal.class).single()).isEqualByComparingTo("-3621");

        // Cloture : les 5 bidons sortent au CMUP ; montant a verser fige
        String detail = lireGet(gerant, "/api/v1/journees/" + jid);
        String cloturee = lire(mvc.perform(avec(post("/api/v1/journees/" + jid + "/cloture"), gerant)
                .content("{\"version\":" + JsonPath.read(detail, "$.version") + "}")).andExpect(status().isOk()));
        stock = lireGet(gerant, "/api/v1/stations/" + station + "/articles");
        a = article(stock, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(a.get("quantite").toString())).isEqualByComparingTo("27");
        assertThat(new BigDecimal(a.get("ventesEnCours").toString())).isZero();
        assertThat(jdbc.sql("select montant_a_verser from tresorerie.recette_journee r join exploitation.journee j on j.id = r.journee_id "
                + "where j.uuid = ?::uuid").param(jid).query(BigDecimal.class).single()).isEqualByComparingTo("98000");
        String mouvements = lireGet(gerant, "/api/v1/stations/" + station + "/articles/mouvements?mois=2026-04");
        assertThat(JsonPath.<List<String>>read(mouvements, "$.mouvements[*].type")).containsExactly("VENTE");
        assertThat(nombre(mouvements, "$.mouvements[0].quantite")).isEqualByComparingTo("-5");
        assertThat(nombre(mouvements, "$.valeurVentes")).isEqualByComparingTo("18106");   // 5 x 3 621,21
        v = lireGet(gerant, "/api/v1/journees/" + jid + "/articles");
        assertThat(JsonPath.<Boolean>read(v, "$.modifiable")).isFalse();
        assertThat(nombre(v, "$.articles[0].cmup")).isEqualByComparingTo("3621.21");

        // Renvoi : les ventes rentrent en stock, la journee redevient modifiable
        mvc.perform(avec(post("/api/v1/journees/" + jid + "/renvoi"), direction)
                        .content("{\"version\":" + JsonPath.read(cloturee, "$.version") + ",\"motif\":\"Vérifier la boutique\"}"))
                .andExpect(status().isOk());
        stock = lireGet(gerant, "/api/v1/stations/" + station + "/articles");
        a = article(stock, "$.articles", "HUILE_ART5");
        assertThat(new BigDecimal(a.get("quantite").toString())).isEqualByComparingTo("32");
        assertThat(new BigDecimal(a.get("cmup").toString())).isEqualByComparingTo("3621.21");
        assertThat(new BigDecimal(a.get("ventesEnCours").toString())).isEqualByComparingTo("5");
        mouvements = lireGet(gerant, "/api/v1/stations/" + station + "/articles/mouvements?mois=2026-04");
        assertThat(JsonPath.<List<Object>>read(mouvements, "$.mouvements")).isEmpty();
    }
}
