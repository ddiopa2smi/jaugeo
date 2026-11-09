package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vue de la Direction : mois arrete a la veille, compare a la meme periode du mois precedent.
 * Les journees cloturees viennent des autres tests (base partagee, ordre libre) : la vue est comparee aux ventes
 * figees lues directement en base.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class DirectionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void laPeriodeVaDuPremierDuMoisALaVeilleEtSeCompareAuMoisPrecedent() throws Exception {
        String direction = OutilsApi.creerEtConnecter(mvc, jdbc, "DIRECTION", "RESEAU", List.of());

        // Le 1er fevrier : tout janvier, compare a tout decembre
        String fevrier = mvc.perform(avec(get("/api/v1/tableau-bord/direction?date=2026-02-01"), direction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mois").value("2026-01"))
                .andExpect(jsonPath("$.periode.du").value("2026-01-01"))
                .andExpect(jsonPath("$.periode.au").value("2026-01-31"))
                .andExpect(jsonPath("$.precedente.du").value("2025-12-01"))
                .andExpect(jsonPath("$.precedente.au").value("2025-12-31"))
                .andExpect(jsonPath("$.jours.length()").value(31))
                .andReturn().getResponse().getContentAsString();
        BigDecimal ventesJanvier = ventes(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(nombre(fevrier, "$.periode.ventes")).isEqualByComparingTo(ventesJanvier);
        assertThat(nombre(fevrier, "$.periode.litres"))
                .isEqualByComparingTo(litres(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertThat(somme(fevrier, "$.jours[*].ventesTotal")).as("série quotidienne").isEqualByComparingTo(ventesJanvier);
        assertThat(somme(fevrier, "$.stations[*].ventes")).as("par station").isEqualByComparingTo(ventesJanvier);
        assertThat(somme(fevrier, "$.produits[*].ventes")).as("par produit").isEqualByComparingTo(ventesJanvier);

        // Le 31 mars : du 1er au 30 mars, compare au 1er-28 fevrier (borne au dernier jour du mois)
        String mars = mvc.perform(avec(get("/api/v1/tableau-bord/direction?date=2026-03-31"), direction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periode.au").value("2026-03-30"))
                .andExpect(jsonPath("$.precedente.du").value("2026-02-01"))
                .andExpect(jsonPath("$.precedente.au").value("2026-02-28"))
                .andReturn().getResponse().getContentAsString();
        BigDecimal ventesFevrier = ventes(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(nombre(mars, "$.precedente.ventes")).isEqualByComparingTo(ventesFevrier);
        assertThat(nombre(mars, "$.ventesMoisPrecedent")).isEqualByComparingTo(ventesFevrier);
        assertThat(somme(mars, "$.stations[*].ventesPrecedent")).isEqualByComparingTo(ventesFevrier);
    }

    @Test
    void reserveAuxRolesDuTableauDeBordEtLimiteAuPerimetre() throws Exception {
        String gerant = OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "NIA")));
        mvc.perform(avec(get("/api/v1/tableau-bord/direction"), gerant)).andExpect(status().isForbidden());

        String superviseur = OutilsApi.creerEtConnecter(mvc, jdbc, "SUPERVISEUR", "STATIONS",
                List.of(numeroStation(jdbc, "NIA"), numeroStation(jdbc, "KAY")));
        String vue = mvc.perform(avec(get("/api/v1/tableau-bord/direction?date=2026-02-01"), superviseur))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(vue, "$.stations[*].code")).containsExactlyInAnyOrder("NIA", "KAY");

        mvc.perform(avec(get("/api/v1/tableau-bord/direction?date=demain"), superviseur))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_INVALIDE"));
    }

    private BigDecimal ventes(LocalDate du, LocalDate au) {
        return jdbc.sql("select coalesce(sum(montant), 0) from exploitation.vente where date_jour between ? and ?")
                .params(du, au).query(BigDecimal.class).single();
    }

    private BigDecimal litres(LocalDate du, LocalDate au) {
        return jdbc.sql("select coalesce(sum(litres), 0) from exploitation.vente where date_jour between ? and ?")
                .params(du, au).query(BigDecimal.class).single();
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(String.valueOf((Object) JsonPath.read(json, chemin)));
    }

    private static BigDecimal somme(String json, String chemin) {
        List<Object> valeurs = JsonPath.read(json, chemin);
        return valeurs.stream().map(v -> new BigDecimal(String.valueOf(v))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
