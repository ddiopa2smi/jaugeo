package sn.digitalperf.jaugeo.approvisionnement.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculApproTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 10);
    private static final BigDecimal REMPLISSAGE = new BigDecimal("0.95");

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static CalculAppro.Cuve cuve(String capacite, String creux, String seuil, String stock, LocalDate date, String ventes) {
        return new CalculAppro.Cuve(d(capacite), d(creux), d(seuil), d(stock), date, d(ventes));
    }

    @Test
    void autonomie_sous_le_delai_plus_la_marge_propose_de_remplir_la_cuve() {
        // Stock fige hier a 9 000 L, 3 000 L/j : 6 000 L ce matin, 3 000 L a l'arrivee (delai 1 j)
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("30000", "500", null, "9000", AUJOURDHUI.minusDays(1), "3000")),
                BigDecimal.ZERO, AUJOURDHUI, 1, 2, REMPLISSAGE);
        assertThat(b.stockAujourdhui()).isEqualByComparingTo("6000");
        assertThat(b.stockArrivee()).isEqualByComparingTo("3000");
        assertThat(b.autonomieJours()).isEqualByComparingTo("1.8");
        assertThat(b.ruptureLe()).isEqualTo(AUJOURDHUI.plusDays(1));
        assertThat(b.capaciteUtile()).isEqualByComparingTo("28500");
        assertThat(b.aCommander()).isTrue();
        // 28 500 - 3 000 = 25 500, arrondi au millier inferieur
        assertThat(b.quantite()).isEqualByComparingTo("25000");
        assertThat(b.motif()).contains("Autonomie de 1.8 j").contains("1 j et 2 j de marge");
    }

    @Test
    void les_livraisons_en_cours_reduisent_la_quantite_proposee() {
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("30000", "500", null, "6000", AUJOURDHUI, "3000")),
                d("3000"), AUJOURDHUI, 1, 2, REMPLISSAGE);
        // Autonomie avec en cours : (6 000 + 3 000 - 500) / 3 000 = 2,8 j
        assertThat(b.aCommander()).isTrue();
        assertThat(b.quantite()).isEqualByComparingTo("22000");
        assertThat(b.motif()).contains("livraisons en cours comprises");
    }

    @Test
    void autonomie_suffisante_rien_a_commander() {
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("30000", "500", null, "20000", AUJOURDHUI, "3000")),
                BigDecimal.ZERO, AUJOURDHUI, 1, 2, REMPLISSAGE);
        assertThat(b.aCommander()).isFalse();
        assertThat(b.quantite()).isNull();
        assertThat(b.motif()).isNull();
    }

    @Test
    void livraisons_en_cours_qui_couvrent_le_besoin() {
        // 8 000 L, 5 000 L/j, 6 000 L en route : autonomie 2,8 j mais 9 500 - 3 000 - 6 000 = 500 L seulement
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("10000", null, null, "8000", AUJOURDHUI, "5000")),
                d("6000"), AUJOURDHUI, 1, 2, REMPLISSAGE);
        assertThat(b.aCommander()).isFalse();
        assertThat(b.quantite()).isNull();
        assertThat(b.motif()).isEqualTo("Les livraisons en cours couvrent le besoin.");
    }

    @Test
    void stock_sous_le_seuil_sans_ventes_connues() {
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("20000", null, "5000", "4000", AUJOURDHUI, null)),
                BigDecimal.ZERO, AUJOURDHUI, 2, 2, REMPLISSAGE);
        assertThat(b.autonomieJours()).isNull();
        assertThat(b.aCommander()).isTrue();
        assertThat(b.quantite()).isEqualByComparingTo("15000");
        assertThat(b.motif()).isEqualTo("Stock sous le seuil de réapprovisionnement.");
    }

    @Test
    void sans_ventes_ni_seuil_l_autonomie_est_inconnue() {
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(cuve("20000", null, null, "4000", AUJOURDHUI, null)),
                BigDecimal.ZERO, AUJOURDHUI, 2, 2, REMPLISSAGE);
        assertThat(b.aCommander()).isFalse();
        assertThat(b.motif()).startsWith("Pas de ventes connues");
    }

    @Test
    void deux_cuves_du_meme_produit_sont_additionnees() {
        CalculAppro.Besoin b = CalculAppro.besoin(List.of(
                        cuve("20000", "300", null, "3000", AUJOURDHUI, "1500"),
                        cuve("10000", "200", null, "2000", AUJOURDHUI, "1000")),
                BigDecimal.ZERO, AUJOURDHUI, 1, 2, REMPLISSAGE);
        // 5 000 L, 2 500 L/j : (5 000 - 500) / 2 500 = 1,8 j ; a l'arrivee 2 500 L ; 28 500 - 2 500 = 26 000
        assertThat(b.ventesMoyennes()).isEqualByComparingTo("2500");
        assertThat(b.autonomieJours()).isEqualByComparingTo("1.8");
        assertThat(b.quantite()).isEqualByComparingTo("26000");
    }

    @Test
    void note_du_transporteur() {
        BigDecimal tol = d("0.5");
        CalculAppro.Note n = CalculAppro.noter(List.of(
                new CalculAppro.Livree(d("10000"), d("0"), true, tol),
                new CalculAppro.Livree(d("10000"), d("20"), true, tol),
                new CalculAppro.Livree(d("10000"), d("30"), false, tol),
                new CalculAppro.Livree(d("10000"), d("80"), true, tol)));
        assertThat(n.livraisons()).isEqualTo(4);
        assertThat(n.horsTolerance()).isEqualTo(1);
        assertThat(n.ponctualitePct()).isEqualByComparingTo("75");
        assertThat(n.conformitePct()).isEqualByComparingTo("75");
        assertThat(n.tauxManquantPct()).isEqualByComparingTo("0.33");
        // 5 x (0,4 x 0,75 + 0,4 x 0,75 + 0,2 x (1 - 0,33 / 1)) = 3,67
        assertThat(n.note()).isEqualByComparingTo("3.7");
    }

    @Test
    void sans_livraison_pas_de_note() {
        assertThat(CalculAppro.noter(List.of()).note()).isNull();
    }

    @Test
    void hors_tolerance_strictement_au_dela() {
        assertThat(CalculAppro.horsTolerance(d("10000"), d("30"), d("0.3"))).isFalse();
        assertThat(CalculAppro.horsTolerance(d("10000"), d("31"), d("0.3"))).isTrue();
        assertThat(CalculAppro.pourcentage(d("31"), d("10000"))).isEqualByComparingTo("0.31");
    }
}
