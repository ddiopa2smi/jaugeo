package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;
import static sn.digitalperf.jaugeo.OutilsApi.uuidStation;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.tarification.Tarification;

/** Sprint 1 : prix dates, baremes de jaugeage, historique des equipements, sur PostgreSQL 18. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class PrixBaremesEvenementsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Tarification tarification;

    @Autowired
    ReferentielConsultation referentiel;

    private String admin() throws Exception {
        return OutilsApi.jetonAdmin(mvc, jdbc);
    }

    private String direction() throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
    }

    private long produitId(String code) {
        return jdbc.sql("select id from referentiel.produit where code = ?").param(code).query(Long.class).single();
    }

    /** Lubrifiant propre au test (non jauge : il se vend dans toutes les stations), pour isoler l'historique. */
    private String nouveauLubrifiant() throws Exception {
        String code = "LUB_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mvc.perform(avec(post("/api/v1/produits"), admin())
                        .content("{\"code\":\"%s\",\"libelle\":\"Huile %s\",\"famille\":\"LUBRIFIANT\",\"unite\":\"UNITE\",\"actif\":true}"
                                .formatted(code, code)))
                .andExpect(status().isCreated());
        return code;
    }

    private static String changement(Instant debut, List<String> stations, String produit, String prix, boolean confirmer) {
        String listeStations = stations.stream().map(s -> "\"" + s + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return """
                {"debut":"%s","motif":"Test","reference":"Arrêté test","stations":[%s],
                 "prix":[{"produitCode":"%s","prixUnitaire":%s}],"confirmerVariation":%s}"""
                .formatted(debut, listeStations, produit, prix, confirmer);
    }

    // ------------------------------------------------------------------ Prix

    @Test
    void lHistoriqueDuClasseurEstChargeEtDecoupeLesPeriodes() {
        long nia = numeroStation(jdbc, "NIA");
        long mbo = numeroStation(jdbc, "MBO");
        long gasoil = produitId("GASOIL");
        long pirogue = produitId("ESSENCE_PIROGUE");

        assertThat(tarification.prixEnVigueur(nia, gasoil, Instant.parse("2026-08-15T23:59:59Z"))).hasValueSatisfying(
                p -> assertThat(p).isEqualByComparingTo("680"));
        assertThat(tarification.prixEnVigueur(nia, gasoil, Instant.parse("2026-08-16T00:00:00Z"))).hasValueSatisfying(
                p -> assertThat(p).isEqualByComparingTo("755"));
        assertThat(tarification.prixEnVigueur(nia, produitId("SUPER"), Instant.parse("2026-09-01T10:00:00Z")))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("990"));
        // Mbour n'a pas de prix avant son ouverture
        assertThat(tarification.prixEnVigueur(mbo, pirogue, Instant.parse("2026-01-15T10:00:00Z"))).isEmpty();
        assertThat(tarification.prixEnVigueur(mbo, pirogue, Instant.parse("2026-02-01T08:00:00Z"))).isPresent();

        // Une journee a cheval sur le changement se decoupe en deux tranches
        List<Tarification.PeriodePrix> tranches = tarification.periodes(nia, gasoil,
                Instant.parse("2026-08-15T06:00:00Z"), Instant.parse("2026-08-16T06:00:00Z"));
        assertThat(tranches).extracting(t -> t.prixUnitaire().intValue()).containsExactly(680, 755);
        assertThat(tranches.get(0).fin()).isEqualTo(Instant.parse("2026-08-16T00:00:00Z"));
    }

    @Test
    void seulLaDirectionModifieLesPrix() throws Exception {
        String corps = changement(Instant.now().plus(1, ChronoUnit.DAYS), List.of(uuidStation(jdbc, "NIA")), "GASOIL", "760", false);
        mvc.perform(avec(post("/api/v1/prix/changements"), admin()).content(corps))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "YOF")));
        mvc.perform(avec(post("/api/v1/prix/changements"), gerant).content(corps)).andExpect(status().isForbidden());

        // Le gerant voit les prix de sa station uniquement
        String grille = mvc.perform(avec(get("/api/v1/prix/grille"), gerant)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(grille, "$[*].stationCode")).isNotEmpty().containsOnly("YOF");
        assertThat(JsonPath.<List<Object>>read(grille, "$[?(@.produitCode=='ESSENCE_PIROGUE')].prix")).containsExactly(497.0);
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "NIA") + "/prix"), gerant)).andExpect(status().isForbidden());
    }

    @Test
    void unChangementProgrammeSAnnuleEtRouvreLePrixPrecedent() throws Exception {
        String dir = direction();
        String lub = nouveauLubrifiant();
        List<String> stations = List.of(uuidStation(jdbc, "NIA"), uuidStation(jdbc, "KAY"));
        Instant j2 = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant j3 = j2.plus(1, ChronoUnit.DAYS);

        String premier = mvc.perform(avec(post("/api/v1/prix/changements"), dir).content(changement(j2, stations, lub, "3500", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes.length()").value(2))
                .andExpect(jsonPath("$.lignes[0].ancienPrix").doesNotExist())
                .andExpect(jsonPath("$.annulable").value(true))
                .andReturn().getResponse().getContentAsString();
        String idPremier = JsonPath.read(premier, "$.id");

        // La grille annonce le prix a venir
        String grille = mvc.perform(avec(get("/api/v1/prix/grille"), dir)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(grille, "$[?(@.produitCode=='%s' && @.stationCode=='KAY')].prochainPrix".formatted(lub)))
                .containsExactly(3500.0);

        // Une date d'effet anterieure au dernier prix est refusee
        mvc.perform(avec(post("/api/v1/prix/changements"), dir)
                        .content(changement(j2.minus(1, ChronoUnit.HOURS), stations, lub, "3600", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRIX_POSTERIEUR_EXISTANT"));

        // +43 % : a confirmer
        mvc.perform(avec(post("/api/v1/prix/changements"), dir).content(changement(j3, stations, lub, "5000", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VARIATION_A_CONFIRMER"));
        String second = mvc.perform(avec(post("/api/v1/prix/changements"), dir).content(changement(j3, stations, lub, "5000", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes[0].ancienPrix").value(3500.0))
                .andReturn().getResponse().getContentAsString();
        String idSecond = JsonPath.read(second, "$.id");

        // On annule d'abord le plus recent
        mvc.perform(avec(post("/api/v1/prix/changements/" + idPremier + "/annulation"), dir))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHANGEMENT_SUIVI"));
        mvc.perform(avec(post("/api/v1/prix/changements/" + idSecond + "/annulation"), dir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annule").value(true));
        assertThat(jdbc.sql("select count(*) from tarification.prix_produit p join referentiel.produit pr on pr.id = p.produit_id "
                + "where pr.code = ? and p.fin is null").param(lub).query(Long.class).single()).isEqualTo(2);
        mvc.perform(avec(post("/api/v1/prix/changements/" + idPremier + "/annulation"), dir)).andExpect(status().isOk());
        mvc.perform(avec(post("/api/v1/prix/changements/" + idPremier + "/annulation"), dir))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_ANNULE"));
        assertThat(jdbc.sql("select count(*) from tarification.prix_produit p join referentiel.produit pr on pr.id = p.produit_id "
                + "where pr.code = ?").param(lub).query(Long.class).single()).isZero();
    }

    @Test
    void unPrixEntreEnVigueurNeSAnnulePlusEtSAppliqueImmediatement() throws Exception {
        String dir = direction();
        String lub = nouveauLubrifiant();
        Instant ilYaUneHeure = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String rep = mvc.perform(avec(post("/api/v1/prix/changements"), dir)
                        .content(changement(ilYaUneHeure, List.of(uuidStation(jdbc, "YOF")), lub, "2750", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.annulable").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(tarification.prixEnVigueur(numeroStation(jdbc, "YOF"), produitId(lub), Instant.now()))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("2750"));
        mvc.perform(avec(post("/api/v1/prix/changements/" + JsonPath.read(rep, "$.id") + "/annulation"), dir))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHANGEMENT_EN_VIGUEUR"));
    }

    @Test
    void unCarburantNeSAppliqueQuAuxStationsQuiLeStockent() throws Exception {
        String dir = direction();
        Instant debut = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        mvc.perform(avec(post("/api/v1/prix/changements"), dir)
                        .content(changement(debut, List.of(uuidStation(jdbc, "NIA"), uuidStation(jdbc, "HYD")), "ESSENCE_PIROGUE", "505", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].stationCode").value("HYD"))
                .andExpect(jsonPath("$.lignes[0].ancienPrix").value(497.0))
                .andExpect(jsonPath("$.ignores[0]").value("NIA : pas de cuve de Essence pirogue"));
        // Une seule station ne vendant pas le produit : rien a creer
        mvc.perform(avec(post("/api/v1/prix/changements"), dir)
                        .content(changement(debut.plus(1, ChronoUnit.DAYS), List.of(uuidStation(jdbc, "KAY")), "SUPER", "990", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUCUN_PRIX_APPLICABLE"));
    }

    @Test
    void laBaseInterditDeuxPrixSimultanes() {
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into tarification.prix_produit (changement_id, station_id, produit_id, prix_unitaire, debut, fin, cree_par, modifie_par)
                        select (select min(id) from tarification.changement_prix), s.id, p.id, 1, '2026-03-01', '2026-03-02', 't', 't'
                        from referentiel.station s, referentiel.produit p where s.code = 'NIA' and p.code = 'GASOIL'""").update())
                .hasMessageContaining("ex_prix_chevauchement");
    }

    // ------------------------------------------------------------------ Baremes

    private String cuve(String station) {
        return jdbc.sql("select c.uuid::text from referentiel.cuve c join referentiel.station s on s.id = c.station_id "
                + "where s.code = ? and c.code = 'C1'").param(station).query(String.class).single();
    }

    private int versionCuve(String uuid) {
        return jdbc.sql("select version from referentiel.cuve where uuid = ?::uuid").param(uuid).query(Integer.class).single();
    }

    private static String bareme(int version, String points) {
        return "{\"certificat\":\"CERT-2026-014\",\"dateCertificat\":\"2026-03-10\",\"version\":%d,\"points\":[%s]}"
                .formatted(version, points);
    }

    @Test
    void unBaremeSeChargeEtConvertitLesHauteursEnLitres() throws Exception {
        String admin = admin();
        String cuve = cuve("YOF");
        String points = "{\"hauteurMm\":0,\"volumeLitres\":0},{\"hauteurMm\":100,\"volumeLitres\":500},{\"hauteurMm\":2000,\"volumeLitres\":20000}";

        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin).content(bareme(versionCuve(cuve), points)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.capaciteLitres").value(20000))   // capacite inconnue : deduite du bareme
                .andExpect(jsonPath("$.certificat").value("CERT-2026-014"));

        mvc.perform(avec(get("/api/v1/cuves/" + cuve + "/bareme/volume?hauteurMm=50"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeLitres").value(250.0));
        assertThat(referentiel.volumeJauge(UUID.fromString(cuve), 1050)).isEqualByComparingTo("10250");
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "YOF") + "/equipements"), admin))
                .andExpect(jsonPath("$.cuves[0].baremePoints").value(3));

        // Version perimee, bareme decroissant
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin).content(bareme(0, points)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_OBSOLETE"));
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin)
                        .content(bareme(versionCuve(cuve), "{\"hauteurMm\":0,\"volumeLitres\":0},{\"hauteurMm\":10,\"volumeLitres\":50},{\"hauteurMm\":20,\"volumeLitres\":40}")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BAREME_NON_CROISSANT"));

        // Remplacement complet : l'ancien bareme disparait
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin)
                        .content(bareme(versionCuve(cuve), "{\"hauteurMm\":0,\"volumeLitres\":0},{\"hauteurMm\":1900,\"volumeLitres\":19800}")))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("select count(*) from referentiel.bareme_cuve b join referentiel.cuve c on c.id = b.cuve_id where c.uuid = ?::uuid")
                .param(cuve).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void unBaremeIncompatibleAvecLaCapaciteEstRefuse() throws Exception {
        String admin = admin();
        String cuve = cuve("KAY");
        mvc.perform(avec(put("/api/v1/cuves/" + cuve), admin)
                        .content("{\"capaciteLitres\":10000,\"creuxSecuriteLitres\":200,\"actif\":true,\"version\":%d}".formatted(versionCuve(cuve))))
                .andExpect(status().isOk());
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), admin)
                        .content(bareme(versionCuve(cuve), "{\"hauteurMm\":0,\"volumeLitres\":0},{\"hauteurMm\":2000,\"volumeLitres\":20000}")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BAREME_INCOHERENT"));
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "KAY")));
        mvc.perform(avec(put("/api/v1/cuves/" + cuve + "/bareme"), gerant)
                        .content(bareme(versionCuve(cuve), "{\"hauteurMm\":0,\"volumeLitres\":0},{\"hauteurMm\":2000,\"volumeLitres\":9000}")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Evenements

    private String pistolet(String station, String code) {
        return jdbc.sql("select p.uuid::text from referentiel.pistolet p join referentiel.pompe po on po.id = p.pompe_id "
                + "join referentiel.station s on s.id = po.station_id where s.code = ? and p.code = ?")
                .param(station).param(code).query(String.class).single();
    }

    private static String evenement(String type, Instant date, String avant, String apres, String extra) {
        return "{\"type\":\"%s\",\"date\":\"%s\"%s%s%s}".formatted(type, date,
                avant == null ? "" : ",\"indexAvant\":" + avant, apres == null ? "" : ",\"indexApres\":" + apres,
                extra == null ? "" : "," + extra);
    }

    @Test
    void lesChangementsDeCompteurFormentUneChaineCoherente() throws Exception {
        String admin = admin();
        String e1 = pistolet("POT", "E1");   // mis en service a l'index 57 500
        Instant t1 = Instant.parse("2026-05-10T09:00:00Z");
        String motif = "\"commentaire\":\"Totalisateur bloqué\"";

        mvc.perform(avec(post("/api/v1/pistolets/" + e1 + "/evenements"), admin)
                        .content(evenement("REMPLACEMENT_TOTALISATEUR", t1, "57000", "0", motif)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INDEX_AVANT_INCOHERENT"));
        mvc.perform(avec(post("/api/v1/pistolets/" + e1 + "/evenements"), admin)
                        .content(evenement("REMPLACEMENT_TOTALISATEUR", t1, "60000", "0", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(avec(post("/api/v1/pistolets/" + e1 + "/evenements"), admin)
                        .content(evenement("REMPLACEMENT_TOTALISATEUR", t1, "60000", "0", motif)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipement").value("Pistolet E1 · pompe P1"));
        mvc.perform(avec(post("/api/v1/pistolets/" + e1 + "/evenements"), admin)
                        .content(evenement("REMISE_A_ZERO", t1.minus(1, ChronoUnit.DAYS), "100", "0", motif)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EVENEMENT_ANTERIEUR"));
        mvc.perform(avec(post("/api/v1/pistolets/" + e1 + "/evenements"), admin)
                        .content(evenement("REMISE_A_ZERO", t1.plus(30, ChronoUnit.DAYS), "4200", "0", motif)))
                .andExpect(status().isCreated());

        List<ReferentielConsultation.ChangementCompteur> chaine = referentiel.changementsCompteur(UUID.fromString(e1),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(chaine).extracting(c -> c.indexAvant().intValue()).containsExactly(60000, 4200);

        // Historique en insertion seule, auteur trace dans l'audit
        assertThatThrownBy(() -> jdbc.sql("update referentiel.evenement_equipement set commentaire = 'x'").update())
                .hasMessageContaining("insertion seule");
        assertThat(jdbc.sql("select distinct utilisateur from audit.journal_audit where table_nom = 'evenement_equipement'")
                .query(String.class).list()).containsExactly("admin");
    }

    @Test
    void chaqueEvenementAppliqueSesRegles() throws Exception {
        String admin = admin();
        Instant hier = Instant.now().minus(1, ChronoUnit.DAYS);
        String pompe = jdbc.sql("select po.uuid::text from referentiel.pompe po join referentiel.station s on s.id = po.station_id "
                + "where s.code = 'HYD' and po.code = 'P1'").query(String.class).single();
        String cuve = cuve("HYD");

        mvc.perform(avec(post("/api/v1/pompes/" + pompe + "/evenements"), admin).content(evenement("POSE_SCELLES", hier, null, null, null)))
                .andExpect(jsonPath("$.code").value("SCELLES_OBLIGATOIRES"));
        mvc.perform(avec(post("/api/v1/pompes/" + pompe + "/evenements"), admin)
                        .content(evenement("POSE_SCELLES", hier, null, null, "\"referenceScelles\":\"DRM 4471-4472\"")))
                .andExpect(status().isCreated());
        mvc.perform(avec(post("/api/v1/pompes/" + pompe + "/evenements"), admin).content(evenement("NETTOYAGE", hier, null, null, null)))
                .andExpect(jsonPath("$.code").value("TYPE_EVENEMENT_INVALIDE"));
        mvc.perform(avec(post("/api/v1/cuves/" + cuve + "/evenements"), admin)
                        .content(evenement("ETALONNAGE", hier, null, null, "\"intervenant\":\"Métrologie légale\",\"prochaineVerification\":\"2027-06-30\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prochaineVerification").value("2027-06-30"));
        mvc.perform(avec(post("/api/v1/cuves/" + cuve + "/evenements"), admin)
                        .content(evenement("MAINTENANCE", Instant.now().plus(2, ChronoUnit.DAYS), null, null, null)))
                .andExpect(jsonPath("$.code").value("EVENEMENT_FUTUR"));

        mvc.perform(avec(get("/api/v1/stations/" + uuidStation(jdbc, "HYD") + "/evenements"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ETALONNAGE"))
                .andExpect(jsonPath("$[1].referenceScelles").value("DRM 4471-4472"));
    }
}
