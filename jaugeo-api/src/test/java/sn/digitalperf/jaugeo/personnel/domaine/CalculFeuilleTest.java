package sn.digitalperf.jaugeo.personnel.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.digitalperf.jaugeo.personnel.domaine.CalculFeuille.Ligne;
import sn.digitalperf.jaugeo.personnel.domaine.CalculFeuille.Pistolet;
import sn.digitalperf.jaugeo.personnel.domaine.CalculFeuille.Rupture;
import sn.digitalperf.jaugeo.personnel.domaine.CalculFeuille.Saisie;

class CalculFeuilleTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static Pistolet pistolet(long id, String code, String depart, String sortie, String prix, Rupture... ruptures) {
        return new Pistolet(id, code, n(depart), sortie == null ? null : n(sortie), prix == null ? null : n(prix), List.of(ruptures));
    }

    private static Saisie saisie(TypePoste poste, long feuille, long pistolet, String fin, String remise) {
        return new Saisie(poste, feuille, pistolet, n(fin), remise == null ? null : n(remise));
    }

    @Test
    void lIndexDeDebutEstLaFinDuPostePrecedent() {
        Map<Long, Pistolet> pistolets = Map.of(1L, pistolet(1, "G1", "1000", "600", "755"));
        // Saisies dans le desordre : le calcul suit l'ordre des postes
        List<Ligne> lignes = CalculFeuille.lignes(pistolets, List.of(
                saisie(TypePoste.SOIR, 20, 1, "1600", null), saisie(TypePoste.MATIN, 10, 1, "1300", null)));
        assertThat(lignes).extracting(l -> l.saisie().poste()).containsExactly(TypePoste.MATIN, TypePoste.SOIR);
        assertThat(lignes.get(0).indexDebut()).isEqualByComparingTo("1000");
        assertThat(lignes.get(0).litres()).isEqualByComparingTo("300");
        assertThat(lignes.get(0).montant()).isEqualByComparingTo("226500");
        assertThat(lignes.get(1).indexDebut()).isEqualByComparingTo("1300");
        assertThat(lignes.get(1).litres()).isEqualByComparingTo("300");
        assertThat(CalculFeuille.problemes(pistolets, lignes)).isEmpty();
        assertThat(CalculFeuille.duParFeuille(lignes)).containsEntry(10L, n("226500")).containsEntry(20L, n("226500"));
    }

    @Test
    void laRemiseEnCuveSeDeduitEtLeMontantSArrondit() {
        Map<Long, Pistolet> pistolets = Map.of(1L, pistolet(1, "S1", "0", "90.5", "990"));
        List<Ligne> lignes = CalculFeuille.lignes(pistolets, List.of(saisie(TypePoste.MATIN, 10, 1, "100.5", "10")));
        assertThat(lignes.get(0).litres()).isEqualByComparingTo("90.5");
        assertThat(lignes.get(0).montant()).isEqualByComparingTo("89595");
        assertThat(CalculFeuille.problemes(pistolets, lignes)).isEmpty();
    }

    @Test
    void lesPostesDoiventTotaliserLesLitresDeLaJournee() {
        Map<Long, Pistolet> pistolets = Map.of(1L, pistolet(1, "G1", "1000", "600", "755"),
                2L, pistolet(2, "G2", "5000", "400", "755"), 3L, pistolet(3, "G3", "0", "0", "755"));
        List<Ligne> lignes = CalculFeuille.lignes(pistolets, List.of(saisie(TypePoste.MATIN, 10, 1, "1300", null)));
        // G1 : 300 L pour 600 ; G2 absent des feuilles alors qu'il a vendu ; G3 n'a rien vendu : rien a signaler
        assertThat(CalculFeuille.problemes(pistolets, lignes)).hasSize(2)
                .anyMatch(p -> p.startsWith("Pistolet G1 : 300 L sur les feuilles de caisse pour 600 L"))
                .anyMatch(p -> p.startsWith("Pistolet G2 : 0 L sur les feuilles de caisse pour 400 L"));
    }

    @Test
    void unIndexQuiReculeEstUneAnomalieSaufChangementDeCompteur() {
        Map<Long, Pistolet> sans = Map.of(1L, pistolet(1, "G1", "1000", null, "755"));
        List<Ligne> lignes = CalculFeuille.lignes(sans, List.of(saisie(TypePoste.MATIN, 10, 1, "900", null)));
        assertThat(lignes.get(0).litres()).isNull();
        assertThat(CalculFeuille.problemes(sans, lignes)).singleElement().asString()
                .startsWith("Poste du matin, pistolet G1 : l'index de fin (900) est inférieur à l'index de début (1000)");
        assertThat(CalculFeuille.duParFeuille(lignes)).containsEntry(10L, BigDecimal.ZERO);

        // Compteur change a 1 200 (repart de 0) : 200 L sur l'ancien + 50 L sur le nouveau
        Map<Long, Pistolet> avec = Map.of(1L, pistolet(1, "G1", "1000", "250", "755", new Rupture(n("1200"), n("0"))));
        lignes = CalculFeuille.lignes(avec, List.of(saisie(TypePoste.MATIN, 10, 1, "50", null)));
        assertThat(lignes.get(0).litres()).isEqualByComparingTo("250");
        assertThat(CalculFeuille.problemes(avec, lignes)).isEmpty();
    }

    @Test
    void sansPrixLesLitresVendusSontSignales() {
        Map<Long, Pistolet> pistolets = Map.of(1L, pistolet(1, "G1", "0", "100", null));
        List<Ligne> lignes = CalculFeuille.lignes(pistolets, List.of(saisie(TypePoste.NUIT, 10, 1, "100", null)));
        assertThat(lignes.get(0).montant()).isNull();
        assertThat(CalculFeuille.problemes(pistolets, lignes)).singleElement().asString().contains("aucun prix en vigueur");
    }
}
