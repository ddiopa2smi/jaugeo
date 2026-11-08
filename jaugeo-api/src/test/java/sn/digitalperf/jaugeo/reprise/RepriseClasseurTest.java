package sn.digitalperf.jaugeo.reprise;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.digitalperf.jaugeo.reprise.ApiJaugeo.corps;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import sn.digitalperf.jaugeo.TestcontainersConfiguration;
import sn.digitalperf.jaugeo.reprise.LecteurClasseur.Affectation;
import sn.digitalperf.jaugeo.reprise.Rapport.Comparaison;
import tools.jackson.databind.JsonNode;

/**
 * Reprise de bout en bout : un classeur synthetique (disposition des stations peche) est rejoue dans une station
 * creee pour le test, par l'API HTTP, et Jaugeo doit retrouver chaque valeur du classeur.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jaugeo.securite.admin-mot-de-passe-initial=" + RepriseClasseurTest.MDP_INITIAL)
class RepriseClasseurTest {

    static final String MDP_INITIAL = "Initial-Reprise-2026";
    static final String MDP_ADMIN = "Siege-Reprise-Test-2026";
    static final String FEUILLE = "STATION REPRISE TEST";

    @LocalServerPort
    int port;

    @TempDir
    Path dossier;

    @Test
    void leClasseurEstRejoueEtRetrouveALIdentique() throws Exception {
        ApiJaugeo api = new ApiJaugeo("http://localhost:" + port);
        ApiJaugeo.Session admin = adminInitialise(api);
        preparerStation(api, admin);
        Path classeur = dossier.resolve("classeur.xlsx");
        ecrireClasseur(classeur);

        Map<String, Affectation> feuilles = Map.of(FEUILLE, new Affectation("RPT", Disposition.standard(false)));
        RepriseClasseur.Options o = new RepriseClasseur.Options(classeur, "http://localhost:" + port,
                LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 3), Set.of("RPT"), "admin", MDP_ADMIN,
                dossier.resolve("rapport.md"), false, feuilles, Map.of(), Map.of());
        Rapport r = RepriseClasseur.executer(o, new PrintStream(OutputStream.nullOutputStream()));

        String md = Files.readString(o.rapport(), StandardCharsets.UTF_8);
        assertThat(r.bilans().get("RPT").arret()).as(md).isNull();
        assertThat(r.bilans().get("RPT").journees()).isEqualTo(3);
        assertThat(r.ecartsJournaliers()).as(md).isEmpty();
        assertThat(r.conforme()).isTrue();

        // Mois : litres, recettes, versements (dont le depot de la caisse d'ouverture), caisse et creances
        Map<String, Comparaison> mois = r.mensuelles().stream()
                .filter(c -> c.mois().equals(YearMonth.of(2025, 3)))
                .collect(Collectors.toMap(Comparaison::grandeur, c -> c));
        for (String g : List.of("Litres vendus ESSENCE_PIROGUE", "Recettes du mois (E)", "Solde de caisse d'ouverture (F)",
                "Versements bancaires (I)", "Solde de caisse du gérant (J)", "Créances en fin de mois (K)",
                "Dépenses autorisées (L)")) {
            assertThat(mois.get(g).ecart()).as(g + "\n" + md).isEqualByComparingTo(BigDecimal.ZERO);
        }
        assertThat(r.constats()).extracting(Classeur.Constat::code).contains("CAISSE_OUVERTURE_VERSEE");

        // Les comptes de reprise sont desactives
        for (JsonNode u : admin.get("/api/v1/utilisateurs")) {
            if (u.path("identifiant").asString().startsWith("reprise.")) {
                assertThat(u.path("actif").asBoolean()).isFalse();
            }
        }

        // Rejouer la meme periode est refuse proprement (journees deja importees)
        Rapport encore = RepriseClasseur.executer(o, new PrintStream(new ByteArrayOutputStream()));
        assertThat(encore.bilans().get("RPT").arret()).contains("prochaine journée");
        assertThat(encore.conforme()).isFalse();
    }

    private static ApiJaugeo.Session adminInitialise(ApiJaugeo api) {
        ApiJaugeo.Session s = api.session("admin", MDP_INITIAL);
        s.changerMotDePasse(MDP_ADMIN);
        return s;
    }

    /** Station peche RPT ouverte le 01/03/2025 : une cuve d'essence pirogue, deux pistolets, prix 500 F. */
    private static void preparerStation(ApiJaugeo api, ApiJaugeo.Session admin) {
        JsonNode station = admin.post("/api/v1/stations", corps("code", "RPT", "nom", "Station reprise test", "type", "PECHE",
                "regionCode", "DK", "modeExploitation", "PROPRE", "dateOuverture", "2025-03-01", "actif", true));
        String id = station.path("id").asString();
        JsonNode cuve = admin.post("/api/v1/stations/" + id + "/cuves", corps("code", "C1", "produitCode", "ESSENCE_PIROGUE",
                "capaciteLitres", 30000, "creuxSecuriteLitres", 0));
        JsonNode pompe = admin.post("/api/v1/stations/" + id + "/pompes", corps("code", "P1", "marque", "Test"));
        String pompeId = pompe.path("id").asString();
        admin.post("/api/v1/pompes/" + pompeId + "/pistolets", corps("code", "E1", "libelle", "Essence 1",
                "cuveId", cuve.path("id").asString(), "indexMiseEnService", 1000));
        admin.post("/api/v1/pompes/" + pompeId + "/pistolets", corps("code", "E2", "libelle", "Essence 2",
                "cuveId", cuve.path("id").asString(), "indexMiseEnService", 2000));

        JsonNode cree = admin.post("/api/v1/utilisateurs", corps("identifiant", "direction.reprise", "nomComplet", "Direction test",
                "role", "DIRECTION", "perimetre", "RESEAU"));
        ApiJaugeo.Session direction = api.session("direction.reprise", cree.path("motDePasseProvisoire").asString());
        direction.changerMotDePasse("Direction-Reprise-2026");
        direction.post("/api/v1/prix/changements", corps("debut", "2025-03-01T00:00:00Z", "motif", "Prix de la reprise",
                "stations", List.of(id), "prix", List.of(corps("produitCode", "ESSENCE_PIROGUE", "prixUnitaire", 500))));
    }

    /**
     * Trois jours a 500 F le litre (colonnes des stations peche) :
     * <ul>
     *   <li>01/03 : 300 + 200 L, jauge 4 480 L pour 4 500 L theoriques (ecart −20 L) ;</li>
     *   <li>02/03 : 200 + 100 L, livraison de 3 000 L (BL 7001), bon direction de 40 L, versement de 250 000 F ;</li>
     *   <li>03/03 : 100 + 100 L, jauge 6 990 L pour 6 980 L (ecart +10 L), depense de 5 000 F, versement de 130 000 F.</li>
     * </ul>
     * Feuille d'analyse de mars : caisse d'ouverture 40 000 F, deposee avec les versements du mois.
     */
    private static void ecrireClasseur(Path fichier) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(fichier)) {
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Sheet s = wb.createSheet(FEUILLE);
            s.createRow(0).createCell(0).setCellValue("SUIVI JOURNALIER");
            //            date  depE1 depE2 arrE1 arrE2 litres ventes  bonsDir aVerser  verse   ref                              dep   stockDep  BL       recu  theo  jauge ecartCumul
            jour(s, style, 4, 1, 1000, 2000, 1300, 2200, 500, 250000, 0, 250000, 0, null, 0, 5000, null, 0, 4500, 4480, -20);
            jour(s, style, 5, 2, 1300, 2200, 1500, 2300, 300, 150000, 40, 130000, 250000, "BV N°123456 DU 02/03/2025 BIS", 0, null, "BL 7001", 3000, 7180, 7180, -20);
            jour(s, style, 6, 3, 1500, 2300, 1600, 2400, 200, 100000, 0, 100000, 130000, "BV N°123457 DU 03/03/2025 CB", 5000, null, null, 0, 6980, 6990, -10);

            Sheet a = wb.createSheet("ANALYSE VENTES & RECETTES");
            a.createRow(0).createCell(0).setCellValue("ANALYSE DES VENTES DU 01/03/2025 AU 31/03/2025");
            Row l = a.createRow(2);
            l.createCell(0).setCellValue(FEUILLE);
            // D litres, E recettes, F caisse d'ouverture, G creances debut, H a verser, I versements, J caisse fin,
            // K creances fin, L depenses, M complements, R ecart
            valeur(l, "D", 1000);
            valeur(l, "E", 500000);
            valeur(l, "F", 40000);
            valeur(l, "G", 0);
            valeur(l, "H", 480000);
            valeur(l, "I", 420000);
            valeur(l, "J", 95000);
            valeur(l, "K", 20000);
            valeur(l, "L", 5000);
            valeur(l, "M", 0);
            valeur(l, "R", 0);
            wb.write(out);
        }
    }

    private static void jour(Sheet s, CellStyle style, int ligne, int jour, double depE1, double depE2, double arrE1,
                             double arrE2, double litres, double ventes, double bonsDirection, double aVerser,
                             double verse, String reference, double depenses, Integer stockDepart, String bl,
                             double recu, double theorique, double jauge, double ecart) {
        Row r = s.createRow(ligne - 1);
        r.createCell(0).setCellValue(LocalDate.of(2025, 3, jour));
        r.getCell(0).setCellStyle(style);
        valeur(r, "B", depE1);
        valeur(r, "C", depE2);
        valeur(r, "D", arrE1);
        valeur(r, "E", arrE2);
        valeur(r, "H", litres);
        valeur(r, "I", 500);
        valeur(r, "J", ventes);
        valeur(r, "K", bonsDirection);
        valeur(r, "O", aVerser);
        valeur(r, "P", verse);
        if (reference != null) {
            r.createCell(CellReference.convertColStringToIndex("S")).setCellValue(reference);
        }
        valeur(r, "Q", depenses);
        if (stockDepart != null) {
            valeur(r, "W", stockDepart);
        }
        if (bl != null) {
            r.createCell(CellReference.convertColStringToIndex("X")).setCellValue(bl);
        }
        valeur(r, "Y", recu);
        valeur(r, "AB", theorique);
        valeur(r, "AD", jauge);
        valeur(r, "AG", ecart);
    }

    private static void valeur(Row r, String colonne, double v) {
        r.createCell(CellReference.convertColStringToIndex(colonne)).setCellValue(v);
    }
}
