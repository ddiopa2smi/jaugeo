package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Parcours de bout en bout sur une vraie base PostgreSQL 18 (Testcontainers) :
 * migrations, compte admin initial, mot de passe impose, jetons, perimetres, regles metier, audit.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=Initial-Test-2026")
@AutoConfigureMockMvc
class ParcoursSecuriteEtReferentielTest {

    private static final String MDP_ADMIN = OutilsApi.MDP_ADMIN;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    // ------------------------------------------------------------------ outils

    private MvcResult connexion(String identifiant, String motDePasse) throws Exception {
        return mvc.perform(post("/api/v1/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"%s\",\"motDePasse\":\"%s\"}".formatted(identifiant, motDePasse)))
                .andReturn();
    }

    private static String jeton(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.jetonAcces");
    }

    private static MockHttpServletRequestBuilder avec(MockHttpServletRequestBuilder req, String jeton) {
        return req.header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON);
    }

    private String jetonAdmin() throws Exception {
        return OutilsApi.jetonAdmin(mvc, jdbc);
    }

    /** Cree un utilisateur, change son mot de passe provisoire et renvoie son jeton. */
    private String creerEtConnecter(String role, String perimetre, List<Long> stations) throws Exception {
        String ident = role.toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
        String stationsJson = stations.toString();
        MvcResult cree = mvc.perform(avec(post("/api/v1/utilisateurs"), jetonAdmin())
                        .content("{\"identifiant\":\"%s\",\"nomComplet\":\"Test %s\",\"role\":\"%s\",\"perimetre\":\"%s\",\"stationIds\":%s}"
                                .formatted(ident, role, role, perimetre, stationsJson)))
                .andExpect(status().isCreated()).andReturn();
        String provisoire = JsonPath.read(cree.getResponse().getContentAsString(), "$.motDePasseProvisoire");
        MvcResult r = connexion(ident, provisoire);
        MvcResult change = mvc.perform(avec(post("/api/v1/auth/mot-de-passe"), jeton(r))
                        .content("{\"ancienMotDePasse\":\"%s\",\"nouveauMotDePasse\":\"Nouveau-Mdp-2026\"}".formatted(provisoire)))
                .andExpect(status().isOk()).andReturn();
        return jeton(change);
    }

    private long numeroStation(String code) {
        return jdbc.sql("select id from referentiel.station where code = ?").param(code).query(Long.class).single();
    }

    private String uuidStation(String code) {
        return jdbc.sql("select uuid::text from referentiel.station where code = ?").param(code).query(String.class).single();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void lesMigrationsChargentLeReseauDuClasseur() {
        // Les autres tests ajoutent des donnees : on ne compte que le reseau charge par la migration V4
        assertThat(jdbc.sql("select count(*) from referentiel.station where cree_par = 'migration'")
                .query(Long.class).single()).isEqualTo(6);
        assertThat(jdbc.sql("select count(*) from referentiel.pistolet where cree_par = 'migration'")
                .query(Long.class).single()).isEqualTo(14);
        assertThat(jdbc.sql("select index_mise_en_service from referentiel.pistolet p join referentiel.pompe po on po.id = p.pompe_id "
                        + "join referentiel.station s on s.id = po.station_id where s.code = 'HYD' and p.code = 'E1'")
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("1747066");
        assertThat(jdbc.sql("select count(*) from securite.role").query(Long.class).single()).isEqualTo(8);
    }

    @Test
    void sansJetonLApiRepond401() throws Exception {
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }

    @Test
    void unMotDePasseProvisoireBloqueLAccesJusquAuChangement() throws Exception {
        jetonAdmin();
        MvcResult cree = mvc.perform(avec(post("/api/v1/utilisateurs"), jetonAdmin())
                        .content("{\"identifiant\":\"prov.%s\",\"nomComplet\":\"Provisoire\",\"role\":\"GERANT\",\"perimetre\":\"RESEAU\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated()).andReturn();
        String ident = JsonPath.read(cree.getResponse().getContentAsString(), "$.utilisateur.identifiant");
        String provisoire = JsonPath.read(cree.getResponse().getContentAsString(), "$.motDePasseProvisoire");

        MvcResult r = connexion(ident, provisoire);
        assertThat((Boolean) JsonPath.read(r.getResponse().getContentAsString(), "$.utilisateur.doitChangerMotDePasse")).isTrue();
        mvc.perform(avec(get("/api/v1/stations"), jeton(r)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));
    }

    @Test
    void leRejeuDUnJetonDeRafraichissementFermeToutesLesSessions() throws Exception {
        jetonAdmin();
        MvcResult r = connexion("admin", MDP_ADMIN);
        Cookie premier = r.getResponse().getCookie("jaugeo_rt");
        assertThat(premier).isNotNull();
        assertThat(premier.isHttpOnly()).isTrue();

        MvcResult rafraichi = mvc.perform(post("/api/v1/auth/rafraichir").cookie(premier))
                .andExpect(status().isOk()).andReturn();
        Cookie second = rafraichi.getResponse().getCookie("jaugeo_rt");

        mvc.perform(post("/api/v1/auth/rafraichir").cookie(premier)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void unGerantNeVoitQueSaStationEtNeModifieRien() throws Exception {
        String jetonGerant = creerEtConnecter("GERANT", "STATIONS", List.of(numeroStation("YOF")));

        mvc.perform(avec(get("/api/v1/stations"), jetonGerant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("YOF"));
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation("KAY")), jetonGerant))
                .andExpect(status().isForbidden());
        mvc.perform(avec(get("/api/v1/stations/" + uuidStation("YOF") + "/equipements"), jetonGerant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pompes.length()").value(2));
        mvc.perform(avec(post("/api/v1/produits"), jetonGerant).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(avec(get("/api/v1/utilisateurs"), jetonGerant))
                .andExpect(status().isForbidden());
    }

    @Test
    void lesReglesDuReferentielSontAppliquees() throws Exception {
        String admin = jetonAdmin();

        // Booleen absent = false ; produit au litre non jauge accepte
        mvc.perform(avec(post("/api/v1/produits"), admin)
                        .content("{\"code\":\"HUILE_5L\",\"libelle\":\"Huile moteur 5 L\",\"famille\":\"LUBRIFIANT\",\"unite\":\"UNITE\",\"actif\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suiviJaugeage").value(false));

        // Jaugeage d'un produit vendu a l'unite : refuse
        mvc.perform(avec(post("/api/v1/produits"), admin)
                        .content("{\"code\":\"GAZ_6KG\",\"libelle\":\"Gaz 6 kg\",\"famille\":\"GAZ\",\"unite\":\"UNITE\",\"suiviJaugeage\":true,\"actif\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("JAUGEAGE_HORS_LITRE"));

        // Code en double
        mvc.perform(avec(post("/api/v1/stations"), admin)
                        .content("{\"code\":\"NIA\",\"nom\":\"Doublon\",\"type\":\"ROUTIERE\",\"regionCode\":\"SL\",\"modeExploitation\":\"PROPRE\",\"actif\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_EXISTANT"));

        // Saisie invalide : detail par champ
        mvc.perform(avec(post("/api/v1/stations"), admin)
                        .content("{\"code\":\"X Y\",\"nom\":\"\",\"type\":\"ROUTIERE\",\"regionCode\":\"SL\",\"modeExploitation\":\"PROPRE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAISIE_INVALIDE"))
                .andExpect(jsonPath("$.champs.nom").exists());

        // Verrouillage optimiste
        String niandane = uuidStation("NIA");
        mvc.perform(avec(put("/api/v1/stations/" + niandane), admin)
                        .content("{\"nom\":\"Station Niandane\",\"type\":\"ROUTIERE\",\"regionCode\":\"SL\",\"modeExploitation\":\"PROPRE\",\"actif\":true,\"version\":42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_OBSOLETE"));

        // Creation d'une station complete : cuve, pompe, pistolet
        MvcResult station = mvc.perform(avec(post("/api/v1/stations"), admin)
                        .content("{\"code\":\"TST\",\"nom\":\"Station test\",\"type\":\"MIXTE\",\"regionCode\":\"DK\",\"modeExploitation\":\"GERANCE_LIBRE\",\"actif\":true}"))
                .andExpect(status().isCreated()).andReturn();
        String idStation = JsonPath.read(station.getResponse().getContentAsString(), "$.id");
        MvcResult cuve = mvc.perform(avec(post("/api/v1/stations/" + idStation + "/cuves"), admin)
                        .content("{\"code\":\"C1\",\"produitCode\":\"GASOIL\",\"capaciteLitres\":30000,\"creuxSecuriteLitres\":500}"))
                .andExpect(status().isCreated()).andReturn();
        String idCuve = JsonPath.read(cuve.getResponse().getContentAsString(), "$.id");
        MvcResult pompe = mvc.perform(avec(post("/api/v1/stations/" + idStation + "/pompes"), admin)
                        .content("{\"code\":\"P1\",\"marque\":\"Tokheim\"}"))
                .andExpect(status().isCreated()).andReturn();
        String idPompe = JsonPath.read(pompe.getResponse().getContentAsString(), "$.id");
        mvc.perform(avec(post("/api/v1/pompes/" + idPompe + "/pistolets"), admin)
                        .content("{\"code\":\"G1\",\"libelle\":\"Gasoil 1\",\"cuveId\":\"%s\",\"indexMiseEnService\":1200.5}".formatted(idCuve)))
                .andExpect(status().isCreated());

        // Un pistolet ne peut pas puiser dans la cuve d'une autre station
        String cuveNiandane = jdbc.sql("select c.uuid::text from referentiel.cuve c join referentiel.station s on s.id = c.station_id where s.code = 'NIA' and c.code = 'C1'")
                .query(String.class).single();
        mvc.perform(avec(post("/api/v1/pompes/" + idPompe + "/pistolets"), admin)
                        .content("{\"code\":\"G2\",\"libelle\":\"Gasoil 2\",\"cuveId\":\"%s\",\"indexMiseEnService\":0}".formatted(cuveNiandane)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUVE_AUTRE_STATION"));
    }

    @Test
    void cinqEchecsVerrouillentLeCompte() throws Exception {
        String admin = jetonAdmin();
        MvcResult cree = mvc.perform(avec(post("/api/v1/utilisateurs"), admin)
                        .content("{\"identifiant\":\"verrou.%s\",\"nomComplet\":\"Verrou\",\"role\":\"POMPISTE\",\"perimetre\":\"RESEAU\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated()).andReturn();
        String ident = JsonPath.read(cree.getResponse().getContentAsString(), "$.utilisateur.identifiant");
        String provisoire = JsonPath.read(cree.getResponse().getContentAsString(), "$.motDePasseProvisoire");

        for (int i = 0; i < 4; i++) {
            assertThat(JsonPath.<String>read(connexion(ident, "Mauvais-" + i).getResponse().getContentAsString(), "$.code"))
                    .isEqualTo("IDENTIFIANTS_INVALIDES");
        }
        assertThat(JsonPath.<String>read(connexion(ident, "Mauvais-5").getResponse().getContentAsString(), "$.code"))
                .isEqualTo("COMPTE_VERROUILLE");
        assertThat(connexion(ident, provisoire).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.sql("select count(*) from securite.journal_connexion where identifiant = ? and not succes")
                .param(ident).query(Long.class).single()).isEqualTo(6);
    }

    @Test
    void lAuditTraceLAuteurEtNeContientAucunSecret() throws Exception {
        String admin = jetonAdmin();
        String yoff = uuidStation("YOF");
        String json = mvc.perform(avec(get("/api/v1/stations/" + yoff), admin)).andReturn().getResponse().getContentAsString();
        Integer version = JsonPath.read(json, "$.version");
        mvc.perform(avec(put("/api/v1/stations/" + yoff), admin)
                        .content("{\"nom\":\"Station Yoff\",\"type\":\"PECHE\",\"regionCode\":\"DK\",\"localite\":\"Yoff Tonghor\",\"modeExploitation\":\"PROPRE\",\"actif\":true,\"version\":%d}".formatted(version)))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("select utilisateur from audit.journal_audit where table_nom = 'station' and operation = 'UPDATE' and cle = ? order by id desc limit 1")
                .param(yoff).query(String.class).single()).isEqualTo("admin");
        assertThat(jdbc.sql("select count(*) from audit.journal_audit where jsonb_exists(nouvelle_valeur, 'mot_de_passe_hash') or jsonb_exists(ancienne_valeur, 'mot_de_passe_hash')")
                .query(Long.class).single()).isZero();
        mvc.perform(avec(get("/api/v1/audit?table=station"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    void lAuteurEstTraceMemeSurLesTablesSansColonneModifiePar() throws Exception {
        String admin = jetonAdmin();
        String ident = "liaison." + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(avec(post("/api/v1/utilisateurs"), admin)
                        .content("{\"identifiant\":\"%s\",\"nomComplet\":\"Liaison\",\"role\":\"GERANT\",\"perimetre\":\"STATIONS\",\"stationIds\":[%d]}"
                                .formatted(ident, numeroStation("KAY"))))
                .andExpect(status().isCreated());
        Long idUtilisateur = jdbc.sql("select id from securite.utilisateur where identifiant = ?").param(ident).query(Long.class).single();
        assertThat(jdbc.sql("select utilisateur from audit.journal_audit where table_nom = 'utilisateur_station' "
                        + "and (nouvelle_valeur ->> 'utilisateur_id')::bigint = ?")
                .param(idUtilisateur).query(String.class).list()).containsExactly("admin");
    }

    @Test
    void leJournalDAuditEstInfalsifiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbc.sql("delete from audit.journal_audit").update())
                .hasMessageContaining("insertion seule");
    }
}
