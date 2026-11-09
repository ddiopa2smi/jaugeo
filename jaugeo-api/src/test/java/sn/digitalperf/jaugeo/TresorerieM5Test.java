package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
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

    private ResultActions importer(String jeton, String format, String station, String nom, byte[] contenu) throws Exception {
        var req = multipart(HttpMethod.POST, "/api/v1/tresorerie/releves").file(new MockMultipartFile("fichier", nom, "text/csv", contenu))
                .param("format", format).header("Authorization", "Bearer " + jeton);
        return mvc.perform(station == null ? req : req.param("station", station));
    }

    private String versement(String date, String reference, long montant) throws Exception {
        return JsonPath.read(creer(gerant, "/api/v1/tresorerie/versements",
                "{\"stationId\":\"%s\",\"dateDepot\":\"%s\",\"banque\":\"CBAO\",\"referenceBv\":\"%s\",\"montant\":%d}"
                        .formatted(station, date, reference, montant)), "$.id");
    }

    private static Map<String, Object> operation(String rapprochement, String libelle) {
        return JsonPath.<List<Map<String, Object>>>read(rapprochement, "$.operations[?(@.libelle=='" + libelle + "')]").get(0);
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

    @Test
    void manquantRembourseSelonUnEcheancier() throws Exception {
        creerStation("TRE");
        String id = journee("2026-07-01", true);
        cloturer(id).andExpect(status().isOk());
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        // Arrete : 75 500 F attendus, 45 500 F comptes, manquant de 30 000 F
        creer(comptable, "/api/v1/stations/" + station + "/arretes", """
                {"date":"2026-07-01","type":"CONTROLE","especesComptees":45500,"responsable":"Keba Mbaye","observation":"Billets manquants"}""");
        String liste = lireGet(comptable, "/api/v1/tresorerie/manquants?statut=OUVERT");
        Map<String, Object> m = JsonPath.<List<Map<String, Object>>>read(liste, "$[?(@.stationCode=='TRE')]").get(0);
        String url = "/api/v1/tresorerie/manquants/" + m.get("id");
        assertThat(new BigDecimal(m.get("reste").toString())).isEqualByComparingTo("30000");

        // Reconnaissance de dette et trois echeances mensuelles de 10 000 F
        mvc.perform(avec(put(url + "/echeancier"), gerant).content("{}")).andExpect(status().isForbidden());
        String e = modifier(comptable, url + "/echeancier", """
                {"version":%s,"reference":"RD-2026-014","reconnuLe":"2026-07-02","nombre":3,"premiereEcheance":"2026-07-31"}"""
                .formatted(m.get("version")));
        assertThat(JsonPath.<String>read(e, "$.reconnaissanceReference")).isEqualTo("RD-2026-014");
        assertThat(JsonPath.<List<String>>read(e, "$.echeances[*].date")).containsExactly("2026-07-31", "2026-08-31", "2026-09-30");
        assertThat(JsonPath.<List<Integer>>read(e, "$.echeances[*].montant")).containsExactly(10000, 10000, 10000);

        // Retenue partielle de 12 000 F : premiere echeance payee, la deuxieme entamee
        e = lire(mvc.perform(avec(post(url + "/paiements"), comptable).content("""
                {"version":%s,"montant":12000,"mode":"RETENUE","reference":"Paie juillet","date":"2026-07-31"}"""
                .formatted(JsonPath.read(e, "$.version").toString()))).andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(e, "$.statut")).isEqualTo("OUVERT");
        assertThat(nombre(e, "$.reste")).isEqualByComparingTo("18000");
        assertThat(JsonPath.<List<String>>read(e, "$.echeances[*].statut").get(0)).isEqualTo("PAYEE");
        assertThat(JsonPath.<List<Integer>>read(e, "$.echeances[*].regle")).containsExactly(10000, 2000, 0);
        // Pas plus que le reste du
        mvc.perform(avec(post(url + "/paiements"), comptable).content("""
                {"version":%s,"montant":20000,"mode":"REMBOURSEMENT","reference":"Recu 12","date":"2026-08-10"}"""
                .formatted(JsonPath.read(e, "$.version").toString()))).andExpect(status().isUnprocessableContent());
        String situation = lireGet(comptable, "/api/v1/tresorerie/situation");
        assertThat(JsonPath.<List<Integer>>read(situation, "$.stations[?(@.stationCode=='TRE')].montantManquants")).containsExactly(18000);

        // Le solde du reste clot le manquant
        e = lire(mvc.perform(avec(post(url + "/reglement"), comptable).content("""
                {"version":%s,"mode":"REMBOURSEMENT","reference":"Recu 13","date":"2026-08-20"}"""
                .formatted(JsonPath.read(e, "$.version").toString()))).andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(e, "$.statut")).isEqualTo("SOLDE");
        assertThat(JsonPath.<List<Integer>>read(e, "$.paiements[*].montant")).containsExactly(12000, 18000);
        assertThat(JsonPath.<List<String>>read(e, "$.echeances[*].statut")).containsOnly("PAYEE");
    }

    @Test
    void releveBancaireLettreLesVersements() throws Exception {
        creerStation("TRB");
        String v1 = versement("2026-06-02", "774401", 123_457);
        String v2 = versement("2026-06-03", "774402", 98_761);
        String v3 = versement("2026-06-03", "774403", 98_761);
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());

        // Format CBAO : point-virgule, dates jj/mm/aaaa, crédit en 5e colonne
        String format = JsonPath.read(creer(comptable, "/api/v1/tresorerie/formats-releve", """
                {"code":"cbao-t","libelle":"CBAO compte recettes","nature":"BANQUE","separateur":";","formatDate":"dd/MM/yyyy",
                 "lignesIgnorees":1,"colDate":1,"colLibelle":2,"colReference":3,"colCredit":5}"""), "$.id");
        mvc.perform(avec(post("/api/v1/tresorerie/formats-releve"), comptable).content("""
                {"code":"CBAO-T","libelle":"Doublon","nature":"BANQUE","separateur":";","formatDate":"dd/MM/yyyy",
                 "lignesIgnorees":1,"colDate":1,"colLibelle":2,"colCredit":5}""")).andExpect(status().isConflict());

        // Export Excel en Windows-1252 : accents, montants à la française, ligne de débit ignorée
        String csv = """
                Date;Libellé;Référence;Débit;Crédit
                03/06/2026;VERSEMENT ESPÈCES BV 774401;;;123 457
                04/06/2026;VERSEMENT ESPÈCES BV 774402;;;98 761,00
                04/06/2026;VERSEMENT ESPÈCES;REF-X;;98761
                05/06/2026;FRAIS TENUE DE COMPTE;;2 500;
                """;
        byte[] fichier = csv.getBytes(Charset.forName("windows-1252"));
        String bilan = lire(importer(comptable, format, null, "cbao-juin.csv", fichier).andExpect(status().isCreated()));
        assertThat(JsonPath.<Integer>read(bilan, "$.operations")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(bilan, "$.ignorees")).isEqualTo(1);
        // BV cité au libellé, puis seul candidat restant de même montant
        assertThat(JsonPath.<Integer>read(bilan, "$.lettrees")).isEqualTo(3);
        importer(comptable, format, null, "cbao-juin.csv", fichier).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELEVE_DEJA_IMPORTE"));

        // Relevé qui chevauche : les opérations déjà importées sont ignorées
        byte[] suite = (csv + "06/06/2026;VERSEMENT ESPÈCES 774499;;;50 000\n").getBytes(Charset.forName("windows-1252"));
        bilan = lire(importer(comptable, format, null, "cbao-juin-bis.csv", suite).andExpect(status().isCreated()));
        assertThat(JsonPath.<Integer>read(bilan, "$.operations")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(bilan, "$.doublons")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(bilan, "$.lettrees")).isZero();

        String r = lireGet(comptable, "/api/v1/tresorerie/rapprochement?mois=2026-06");
        assertThat(JsonPath.<Map<String, Object>>read(operation(r, "VERSEMENT ESPÈCES BV 774401"), "$.versement").get("id")).isEqualTo(v1);
        assertThat(JsonPath.<String>read(operation(r, "VERSEMENT ESPÈCES BV 774402"), "$.versement.id")).isEqualTo(v2);
        assertThat(JsonPath.<String>read(operation(r, "VERSEMENT ESPÈCES"), "$.versement.id")).isEqualTo(v3);
        Map<String, Object> ouverte = operation(r, "VERSEMENT ESPÈCES 774499");
        assertThat(ouverte.get("versement")).isNull();
        assertThat(JsonPath.<Boolean>read(r, "$.peutLettrer")).isTrue();
        assertThat(nombre(r, "$.taux")).isPositive();
        // Le versement porte la date de son crédit au relevé
        String liste = lireGet(gerant, "/api/v1/tresorerie/versements?station=" + station + "&mois=2026-06");
        assertThat(JsonPath.<List<String>>read(liste, "$[?(@.referenceBv=='774401')].dateReleve")).containsExactly("2026-06-03");
        // Réservé au siège
        mvc.perform(avec(get("/api/v1/tresorerie/rapprochement?mois=2026-06"), gerant)).andExpect(status().isForbidden());

        // Versement saisi après coup : lettrage manuel, défait, puis automatique
        String v4 = versement("2026-06-05", "774499", 50_000);
        String op = ouverte.get("id").toString();
        mvc.perform(avec(put("/api/v1/tresorerie/rapprochement/operations/" + op + "/lettrage"), comptable)
                .content("{\"versementId\":\"" + v1 + "\"}")).andExpect(status().isConflict());
        String l = modifier(comptable, "/api/v1/tresorerie/rapprochement/operations/" + op + "/lettrage", "{\"versementId\":\"" + v4 + "\"}");
        assertThat(JsonPath.<String>read(l, "$.modeLettrage")).isEqualTo("MANUEL");
        lire(mvc.perform(avec(delete("/api/v1/tresorerie/rapprochement/operations/" + op + "/lettrage"), comptable)).andExpect(status().isOk()));
        String auto = lire(mvc.perform(avec(post("/api/v1/tresorerie/rapprochement/lettrage-automatique"), comptable)).andExpect(status().isOk()));
        assertThat(JsonPath.<Integer>read(auto, "$.lettrees")).isPositive();
        r = lireGet(comptable, "/api/v1/tresorerie/rapprochement?mois=2026-06");
        assertThat(JsonPath.<String>read(operation(r, "VERSEMENT ESPÈCES 774499"), "$.versement.id")).isEqualTo(v4);
        assertThat(JsonPath.<String>read(operation(r, "VERSEMENT ESPÈCES 774499"), "$.modeLettrage")).isEqualTo("AUTO");
    }

    @Test
    void releveWaveComparéAuxEncaissementsDeclares() throws Exception {
        creerStation("TRW");
        String id = journee("2026-06-10", true);
        String e = lireGet(gerant, "/api/v1/journees/" + id + "/encaissements");
        modifier(gerant, "/api/v1/journees/" + id + "/encaissements",
                "{\"version\":%s,\"encaissements\":[{\"mode\":\"WAVE\",\"montant\":20000}]}".formatted(JsonPath.read(e, "$.version").toString()));
        String comptable = OutilsApi.creerEtConnecter(mvc, jdbc, "COMPTABLE", "RESEAU", List.of());
        String format = JsonPath.read(creer(comptable, "/api/v1/tresorerie/formats-releve", """
                {"code":"WAVE-T","libelle":"Wave Business","nature":"WAVE","separateur":",","formatDate":"yyyy-MM-dd",
                 "lignesIgnorees":1,"colDate":1,"colLibelle":2,"colReference":3,"colCredit":4}"""), "$.id");
        byte[] wave = """
                date,description,transaction,montant
                2026-06-10 08:15,Paiement client,T1,12000
                2026-06-10 19:40,"Paiement client, pompe 2",T2,6500
                2026-06-11 09:00,Paiement client,T3,4000
                """.getBytes(StandardCharsets.UTF_8);
        // Un relevé marchand se rattache à la station du compte Wave
        importer(comptable, format, null, "wave.csv", wave).andExpect(status().isUnprocessableContent());
        String bilan = lire(importer(comptable, format, station, "wave.csv", wave).andExpect(status().isCreated()));
        assertThat(JsonPath.<Integer>read(bilan, "$.operations")).isEqualTo(3);

        String r = lireGet(comptable, "/api/v1/tresorerie/rapprochement/electronique?mois=2026-06");
        List<Map<String, Object>> lignes = JsonPath.read(r, "$.lignes[?(@.stationCode=='TRW')]");
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).containsEntry("date", "2026-06-10").containsEntry("mode", "WAVE");
        assertThat(new BigDecimal(lignes.get(0).get("declare").toString())).isEqualByComparingTo("20000");
        assertThat(new BigDecimal(lignes.get(0).get("releve").toString())).isEqualByComparingTo("18500");
        assertThat(new BigDecimal(lignes.get(0).get("ecart").toString())).isEqualByComparingTo("-1500");
        assertThat(new BigDecimal(lignes.get(1).get("ecart").toString())).isEqualByComparingTo("4000");
        assertThat(JsonPath.<Integer>read(r, "$.ecarts")).isGreaterThanOrEqualTo(2);
    }
}
