package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Sprint 6 : tableau de bord du reseau (niveau d'alerte, volumes, ecarts, caisse par station). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class TableauBordTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void leReseauEstClasseParNiveauDAlerte() throws Exception {
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());

        // Un an apres les journees des tests : aucune station n'a de journee cloturee recente
        String json = mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=2027-06-15"), direction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2027-06-15"))
                .andExpect(jsonPath("$.mois").value("2027-06"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> stations = JsonPath.read(json, "$.stations");
        assertThat(stations).extracting(s -> s.get("code")).contains("NIA", "HYD", "KAY", "POT", "YOF", "MBO");
        Map<String, Object> nia = stations.stream().filter(s -> "NIA".equals(s.get("code"))).findFirst().orElseThrow();
        assertThat(nia.get("niveau")).isEqualTo("ENQUETE");
        assertThat((List<String>) JsonPath.read(json, "$.stations[?(@.code == 'NIA')].motifs[0]"))
                .containsExactly("Aucune journée clôturée depuis 60 jours");
        assertThat(nia.get("latitude")).isNotNull();
        assertThat(((Number) JsonPath.read(json, "$.parNiveau.ENQUETE")).intValue()).isEqualTo(stations.size());

        // Jour du sprint 2 : Niandane a des journees cloturees (JourneesTest) ; volumes du mois presents
        String janvier = mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=2026-01-02"), direction))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(janvier, "$.stations[?(@.code == 'MBO')]")).as("Mbour ouvre le 01/02/2026").isEmpty();

        // Date invalide
        mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=15/06/2027"), direction))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_INVALIDE"));
    }

    @Test
    void reserveAuxRolesDuSiegeEtAuxSuperviseurs() throws Exception {
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "NIA")));
        mvc.perform(avec(get("/api/v1/tableau-bord/reseau"), gerant)).andExpect(status().isForbidden());

        // Superviseur de tout le reseau (V13) : toutes les stations
        String superviseurReseau = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "RESEAU", List.of());
        String reseau = mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=2027-06-15"), superviseurReseau))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(reseau, "$.stations[*].code")).contains("NIA", "HYD", "KAY", "POT", "YOF", "MBO");

        // Superviseur de quelques stations : seulement les siennes
        String superviseurNord = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "STATIONS",
                List.of(numeroStation(jdbc, "NIA"), numeroStation(jdbc, "KAY")));
        String nord = mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=2027-06-15"), superviseurNord))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(nord, "$.stations[*].code")).containsExactlyInAnyOrder("NIA", "KAY");
    }

    @Test
    void lesReponsesPortentLesEntetesDeSecurite() throws Exception {
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());
        mvc.perform(avec(get("/api/v1/tableau-bord/reseau?date=2027-06-15"), direction).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
