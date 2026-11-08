package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sprint 6 : une saisie rejouee par la tablette apres une coupure (meme Idempotency-Key) n'est executee qu'une fois.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class IdempotenceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private static String produit(String code, String libelle) {
        return "{\"code\":\"%s\",\"libelle\":\"%s\",\"famille\":\"LUBRIFIANT\",\"unite\":\"UNITE\",\"actif\":true}"
                .formatted(code, libelle);
    }

    @Test
    void uneSaisieRejoueeNEstExecuteeQuUneFois() throws Exception {
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String code = "ID" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        String cle = "tablette-" + UUID.randomUUID();

        String premiere = mvc.perform(avec(post("/api/v1/produits"), admin).header("Idempotency-Key", cle)
                        .content(produit(code, "Huile 1 L")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andReturn().getResponse().getContentAsString();

        // Meme envoi rejoue : meme reponse, rien de cree en plus
        String rejouee = mvc.perform(avec(post("/api/v1/produits"), admin).header("Idempotency-Key", cle)
                        .content(produit(code, "Huile 1 L")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(rejouee).isEqualTo(premiere);
        assertThat(jdbc.sql("select count(*) from referentiel.produit where code = ?").param(code).query(Long.class).single())
                .isEqualTo(1);

        // Meme cle pour une autre saisie : refusee
        mvc.perform(avec(post("/api/v1/produits"), admin).header("Idempotency-Key", cle)
                        .content(produit(code, "Huile 2 L")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCE_CLE_REUTILISEE"));

        // Sans cle, le second envoi est bien execute (et refuse par la regle metier)
        mvc.perform(avec(post("/api/v1/produits"), admin).content(produit(code, "Huile 1 L")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_EXISTANT"));

        // Un refus metier est memorise lui aussi : le rejeu renvoie le meme refus
        String autreCle = "tablette-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(avec(post("/api/v1/produits"), admin).header("Idempotency-Key", autreCle)
                            .content(produit(code, "Huile 1 L")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CODE_EXISTANT"));
        }

        // Cle invalide
        mvc.perform(avec(post("/api/v1/produits"), admin).header("Idempotency-Key", "court")
                        .content(produit(code, "Huile 1 L")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCE_CLE_INVALIDE"));
    }

    @Test
    void uneReponsePassagereNEstPasMemorisee() throws Exception {
        String cle = "tablette-" + UUID.randomUUID();
        // Sans jeton : 401, rien n'est memorise (la tablette rejouera apres reconnexion)
        mvc.perform(post("/api/v1/produits").header("Idempotency-Key", cle).contentType("application/json")
                        .content(produit("IDX", "X")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("select count(*) from securite.requete_idempotente where cle = ?").param(cle)
                .query(Long.class).single()).isZero();
    }
}
