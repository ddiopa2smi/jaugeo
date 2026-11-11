package sn.digitalperf.jaugeo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sn.digitalperf.jaugeo.OutilsApi.avec;
import static sn.digitalperf.jaugeo.OutilsApi.numeroStation;
import static sn.digitalperf.jaugeo.OutilsApi.uuidStation;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sprint 4 : depenses autorisees, montant a verser, versements (BV), solde de caisse du gerant, arretes et manquants.
 * Station reservee a ces tests : Potou, en janvier 2026 (les tests du sprint 1 n'y touchent qu'a partir de mai).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jaugeo.securite.admin-mot-de-passe-initial=" + OutilsApi.MDP_INITIAL)
@AutoConfigureMockMvc
class TresorerieTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String gerant() throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, "GERANT", "STATIONS", List.of(numeroStation(jdbc, "POT")));
    }

    private String role(String role) throws Exception {
        return OutilsApi.creerEtConnecter(mvc, jdbc, role, "RESEAU", List.of());
    }

    private String lire(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String obtenir(String jeton, String url) throws Exception {
        return lire(mvc.perform(avec(get(url), jeton)).andExpect(status().isOk()));
    }

    private String nature(String jeton, String code) throws Exception {
        return JsonPath.<List<String>>read(obtenir(jeton, "/api/v1/depenses/natures"), "$[?(@.code=='" + code + "')].id").get(0);
    }

    private ResultActions depense(String jeton, String journee, Object version, String nature, String libelle, long montant)
            throws Exception {
        return mvc.perform(avec(post("/api/v1/journees/" + journee + "/depenses"), jeton)
                .content("{\"version\":%s,\"natureId\":\"%s\",\"libelle\":\"%s\",\"beneficiaire\":\"Quincaillerie Fall\",\"montant\":%d}"
                        .formatted(version, nature, libelle, montant)));
    }

    private ResultActions joindre(String jeton, String url, Object version, String nom, byte[] contenu) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, url).file(new MockMultipartFile("fichier", nom, "image/png", contenu))
                .param("version", String.valueOf(version)).header("Authorization", "Bearer " + jeton));
    }

    private static String versement(String station, String date, String banque, String reference, long montant) {
        return "{\"stationId\":\"%s\",\"dateDepot\":\"%s\",\"banque\":\"%s\",\"referenceBv\":\"%s\",\"montant\":%d,\"deposant\":\"Keba Mbaye\"}"
                .formatted(station, date, banque, reference, montant);
    }

    private static BigDecimal nombre(String json, String chemin) {
        return new BigDecimal(JsonPath.read(json, chemin).toString());
    }

    @Test
    void montantAVerserVersementsEtEcartDeCaisseAPotou() throws Exception {
        String gerant = gerant();
        String superviseur = role("SUPERVISEUR");
        String comptable = role("COMPTABLE");
        String pot = uuidStation(jdbc, "POT");

        // Reprise du solde de caisse au 31/12/2025 (classeur : 39 760 F) ; reservee aux controleurs
        String reprise = "{\"date\":\"2025-12-31\",\"type\":\"REPRISE\",\"especesComptees\":39760}";
        mvc.perform(avec(post("/api/v1/stations/" + pot + "/arretes"), gerant).content(reprise)).andExpect(status().isForbidden());
        mvc.perform(avec(post("/api/v1/stations/" + pot + "/arretes"), comptable).content(reprise))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ecart").doesNotExist());

        // Journee du 01/01 : 100 L par pistolet a 497 F
        String j = lire(mvc.perform(avec(post("/api/v1/stations/" + pot + "/journees"), gerant).content("{\"date\":\"2026-01-01\"}"))
                .andExpect(status().isCreated()));
        String id = JsonPath.read(j, "$.id");
        List<Map<String, Object>> lignes = JsonPath.read(j, "$.lignes");
        List<String> releves = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            releves.add("{\"pistoletId\":\"" + l.get("pistoletId") + "\",\"indexArrivee\":"
                    + new BigDecimal(l.get("indexDepart").toString()).add(BigDecimal.valueOf(100)).toPlainString() + "}");
        }
        j = lire(mvc.perform(avec(put("/api/v1/journees/" + id + "/releves"), gerant)
                .content("{\"version\":" + JsonPath.read(j, "$.version") + ",\"releves\":[" + String.join(",", releves) + "]}"))
                .andExpect(status().isOk()));
        BigDecimal ventes = BigDecimal.valueOf(100L * 497 * lignes.size());

        // Depenses : 5 000 F de fournitures (sous le plafond), 60 000 F d'entretien (plafond 50 000 F : a valider)
        String d = lire(depense(gerant, id, JsonPath.read(j, "$.version"), nature(gerant, "FOURNITURES"), "Registre et stylos", 5000)
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(d, "$.depenses[0].statut")).isEqualTo("AUTORISEE");
        d = lire(depense(gerant, id, JsonPath.read(d, "$.version"), nature(gerant, "ENTRETIEN"), "Réparation du flexible P1", 60000)
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(d, "$.depenses[1].statut")).isEqualTo("A_VALIDER");
        assertThat(nombre(d, "$.totalEnAttente")).isEqualByComparingTo("60000");
        assertThat(JsonPath.<List<String>>read(d, "$.anomalies")).hasSize(3);   // decision + deux justificatifs

        j = OutilsApi.jaugerSansEcart(mvc, gerant, id);
        mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(j, "$.version") + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("attend la validation du superviseur")));

        // Justificatifs : le type est controle sur le contenu
        d = obtenir(gerant, "/api/v1/journees/" + id + "/depenses");
        String fournitures = JsonPath.read(d, "$.depenses[0].id");
        String entretien = JsonPath.read(d, "$.depenses[1].id");
        String urlPiece = "/api/v1/journees/" + id + "/depenses/" + fournitures + "/justificatif";
        joindre(gerant, urlPiece, JsonPath.read(d, "$.version"), "facture.png", "<script>".getBytes())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TYPE_FICHIER_REFUSE"));
        d = lire(joindre(gerant, urlPiece, JsonPath.read(d, "$.version"), "facture.jpg", JPEG).andExpect(status().isOk()));
        d = lire(joindre(gerant, "/api/v1/journees/" + id + "/depenses/" + entretien + "/justificatif", JsonPath.read(d, "$.version"),
                "devis.png", PNG).andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(d, "$.depenses[1].justificatif.typeMime")).isEqualTo("image/png");
        mvc.perform(avec(get("/api/v1/depenses/" + fournitures + "/justificatif"), superviseur))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));

        // Validation hors plafond : superviseur seulement, motif exige pour un refus
        String file = obtenir(superviseur, "/api/v1/depenses/a-valider");
        assertThat(JsonPath.<List<String>>read(file, "$[?(@.stationCode=='POT')].libelle")).containsExactly("Réparation du flexible P1");
        String decision = "/api/v1/journees/" + id + "/depenses/" + entretien + "/decision";
        mvc.perform(avec(post(decision), gerant).content("{\"version\":" + JsonPath.read(d, "$.version") + ",\"autoriser\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(avec(post(decision), superviseur).content("{\"version\":" + JsonPath.read(d, "$.version") + ",\"autoriser\":false}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        d = lire(mvc.perform(avec(post(decision), superviseur)
                        .content("{\"version\":" + JsonPath.read(d, "$.version") + ",\"autoriser\":true,\"motif\":\"Devis conforme\"}"))
                .andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(d, "$.anomalies")).isEmpty();

        // Apercu de la recette : ventes - 65 000 F de depenses, solde d'ouverture repris
        String r = obtenir(gerant, "/api/v1/journees/" + id + "/tresorerie");
        BigDecimal montant = ventes.subtract(BigDecimal.valueOf(65_000));
        assertThat(nombre(r, "$.ventes")).isEqualByComparingTo(ventes);
        assertThat(nombre(r, "$.depenses")).isEqualByComparingTo("65000");
        assertThat(nombre(r, "$.montantAVerser")).isEqualByComparingTo(montant);
        assertThat(nombre(r, "$.soldeOuverture")).isEqualByComparingTo("39760");
        assertThat(JsonPath.<Boolean>read(r, "$.fige")).isFalse();

        String c = lire(mvc.perform(avec(post("/api/v1/journees/" + id + "/cloture"), gerant).content("{\"version\":" + JsonPath.read(d, "$.version") + "}"))
                .andExpect(status().isOk()));
        assertThat(jdbc.sql("select montant_a_verser from tresorerie.recette_journee r join exploitation.journee j on j.id = r.journee_id where j.uuid = ?::uuid")
                .param(id).query(BigDecimal.class).single()).isEqualByComparingTo(montant);
        mvc.perform(avec(post("/api/v1/journees/" + id + "/depenses"), gerant)
                        .content("{\"version\":%s,\"natureId\":\"%s\",\"libelle\":\"x\",\"montant\":10}".formatted(JsonPath.read(c, "$.version"), nature(gerant, "AUTRE"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("JOURNEE_CLOTUREE"));

        // Versement du lendemain ; bordereau unique par banque, jamais dans le futur
        long verse = montant.longValue() - 20_000;
        String v = lire(mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant).content(versement(pot, "2026-01-02", "Cbao", "bv 774378", verse)))
                .andExpect(status().isCreated()));
        String vid = JsonPath.read(v, "$.id");
        assertThat(JsonPath.<String>read(v, "$.referenceBv")).isEqualTo("BV 774378");
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant).content(versement(pot, "2026-01-03", "CBAO", "BV  774378", 1000)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BV_DEJA_SAISI"));
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant)
                        .content(versement(pot, LocalDate.now().plusDays(2).toString(), "SGBS", "A-1", 1000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("DATE_DEPOT_INVALIDE"));
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), superviseur).content(versement(pot, "2026-01-02", "SGBS", "A-2", 1000)))
                .andExpect(status().isForbidden());

        // La banque se choisit dans la table des banques : 29 banques du Senegal, seules les actives sont proposees
        String admin = OutilsApi.jetonAdmin(mvc, jdbc);
        String banques = obtenir(gerant, "/api/v1/tresorerie/banques?toutes=true");
        assertThat(JsonPath.<List<String>>read(banques, "$[*].code")).hasSizeGreaterThanOrEqualTo(29).contains("CBAO", "BIS", "LBA", "LBO");
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant).content(versement(pot, "2026-01-02", "SGBS", "A-3", 1000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BANQUE_INCONNUE"));
        String lbo = JsonPath.<List<String>>read(banques, "$[?(@.code=='LBO')].id").get(0);
        mvc.perform(avec(put("/api/v1/tresorerie/banques/" + lbo), gerant).content("{\"actif\":false}")).andExpect(status().isForbidden());
        mvc.perform(avec(put("/api/v1/tresorerie/banques/" + lbo), admin).content("{\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        assertThat(JsonPath.<List<String>>read(obtenir(gerant, "/api/v1/tresorerie/banques"), "$[*].code")).contains("CBAO").doesNotContain("LBO");
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant).content(versement(pot, "2026-01-02", "LBO", "A-4", 1000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BANQUE_INACTIVE"));
        mvc.perform(avec(put("/api/v1/tresorerie/banques/" + lbo), admin).content("{\"actif\":true}")).andExpect(status().isOk());
        mvc.perform(avec(post("/api/v1/tresorerie/banques"), admin).content("{\"code\":\"bqt\",\"nom\":\"Banque de test\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("BQT")).andExpect(jsonPath("$.actif").value(true));
        mvc.perform(avec(post("/api/v1/tresorerie/banques"), admin).content("{\"code\":\"BQT\",\"nom\":\"Doublon\"}"))
                .andExpect(status().isConflict());

        // Verification comptable : bordereau joint exige, l'auteur ne verifie pas lui-meme
        String verification = "/api/v1/tresorerie/versements/" + vid + "/verification";
        mvc.perform(avec(post(verification), comptable).content("{\"version\":" + JsonPath.read(v, "$.version") + ",\"valide\":true}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PIECE_MANQUANTE"));
        v = lire(joindre(gerant, "/api/v1/tresorerie/versements/" + vid + "/piece", JsonPath.read(v, "$.version"), "bv.png", PNG)
                .andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(v, "$.piece.nomFichier")).isEqualTo("bv.png");
        assertThat(JsonPath.<List<String>>read(obtenir(comptable, "/api/v1/tresorerie/versements"), "$[*].id")).contains(vid);
        mvc.perform(avec(post(verification), gerant).content("{\"version\":" + JsonPath.read(v, "$.version") + ",\"valide\":true}"))
                .andExpect(status().isForbidden());
        v = lire(mvc.perform(avec(post(verification), comptable).content("{\"version\":" + JsonPath.read(v, "$.version") + ",\"valide\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VERIFIE")));
        mvc.perform(avec(put("/api/v1/tresorerie/versements/" + vid), gerant)
                        .content(versement(pot, "2026-01-02", "CBAO", "BV 774378", 5).replace("{", "{\"version\":" + JsonPath.read(v, "$.version") + ",")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VERSEMENT_TRAITE"));

        // Livre de caisse de janvier : solde non verse jour par jour
        String livre = obtenir(gerant, "/api/v1/stations/" + pot + "/caisse?mois=2026-01");
        BigDecimal soldeJ1 = montant.add(BigDecimal.valueOf(39_760));
        assertThat(nombre(livre, "$.soldeDebut")).isEqualByComparingTo("39760");
        assertThat(nombre(livre, "$.lignes[0].montantAVerser")).isEqualByComparingTo(montant);
        assertThat(nombre(livre, "$.lignes[0].solde")).isEqualByComparingTo(soldeJ1);
        assertThat(JsonPath.<String>read(livre, "$.lignes[1].date")).isEqualTo("2026-01-02");
        assertThat(nombre(livre, "$.lignes[1].versements")).isEqualByComparingTo(String.valueOf(verse));
        assertThat(nombre(livre, "$.lignes[1].solde")).isEqualByComparingTo(soldeJ1.subtract(BigDecimal.valueOf(verse)));

        // Arrete de caisse du 01/01 : 1 500 F manquent, observation exigee ; un manquant est ouvert
        String arretes = "/api/v1/stations/" + pot + "/arretes";
        mvc.perform(avec(post(arretes), superviseur).content("{\"date\":\"2026-01-02\",\"type\":\"CONTROLE\",\"especesComptees\":1000}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("JOURNEE_NON_CLOTUREE"));
        String compte = soldeJ1.subtract(BigDecimal.valueOf(1_500)).toPlainString();
        mvc.perform(avec(post(arretes), superviseur).content("{\"date\":\"2026-01-01\",\"type\":\"CONTROLE\",\"especesComptees\":" + compte + "}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OBSERVATION_OBLIGATOIRE"));
        String a = lire(mvc.perform(avec(post(arretes), superviseur).content("{\"date\":\"2026-01-01\",\"type\":\"CONTROLE\",\"especesComptees\":"
                        + compte + ",\"responsable\":\"Keba Mbaye\",\"observation\":\"Billets manquants au comptage\"}"))
                .andExpect(status().isCreated()));
        assertThat(nombre(a, "$.soldeTheorique")).isEqualByComparingTo(soldeJ1);
        assertThat(nombre(a, "$.ecart")).isEqualByComparingTo("-1500");
        assertThat(JsonPath.<String>read(a, "$.manquantStatut")).isEqualTo("OUVERT");

        // La caisse arretee fige le passe : ni renvoi de la journee, ni versement a cette date
        mvc.perform(avec(post("/api/v1/journees/" + id + "/renvoi"), superviseur)
                        .content("{\"version\":" + JsonPath.read(c, "$.version") + ",\"motif\":\"Contrôle\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ARRETE_CAISSE_POSTERIEUR"));
        mvc.perform(avec(post("/api/v1/tresorerie/versements"), gerant).content(versement(pot, "2026-01-01", "BICIS", "B-1", 1000)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAISSE_ARRETEE"));
        livre = obtenir(gerant, "/api/v1/stations/" + pot + "/caisse?mois=2026-01");
        assertThat(nombre(livre, "$.lignes[0].ecart")).isEqualByComparingTo("-1500");
        assertThat(nombre(livre, "$.lignes[0].solde")).isEqualByComparingTo(compte);
        assertThat(nombre(livre, "$.lignes[1].solde")).isEqualByComparingTo(new BigDecimal(compte).subtract(BigDecimal.valueOf(verse)));

        // Situation du reseau et reglement du manquant par la comptabilite
        String situation = obtenir(comptable, "/api/v1/tresorerie/situation");
        assertThat(JsonPath.<List<Object>>read(situation, "$.stations[?(@.stationCode=='POT')].soldeNonVerse").get(0).toString())
                .isEqualTo(new BigDecimal(compte).subtract(BigDecimal.valueOf(verse)).toPlainString());
        assertThat(JsonPath.<List<Integer>>read(situation, "$.stations[?(@.stationCode=='POT')].manquantsOuverts")).containsExactly(1);
        String m = obtenir(comptable, "/api/v1/tresorerie/manquants?statut=OUVERT");
        List<Map<String, Object>> potou = JsonPath.read(m, "$[?(@.stationCode=='POT')]");
        String reglement = "/api/v1/tresorerie/manquants/" + potou.get(0).get("id") + "/reglement";
        String corps = "{\"version\":" + potou.get(0).get("version") + ",\"mode\":\"RETENUE\",\"reference\":\"Paie janvier 2026\",\"date\":\"2026-01-31\"}";
        mvc.perform(avec(post(reglement), gerant).content(corps)).andExpect(status().isForbidden());
        mvc.perform(avec(post(reglement), comptable).content(corps))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SOLDE")).andExpect(jsonPath("$.reglable").value(false));
    }
}
