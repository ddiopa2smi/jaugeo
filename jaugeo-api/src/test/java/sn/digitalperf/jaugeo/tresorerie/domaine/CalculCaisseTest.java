package sn.digitalperf.jaugeo.tresorerie.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculCaisseTest {

    private static BigDecimal f(long v) {
        return BigDecimal.valueOf(v);
    }

    private static final LocalDate J1 = LocalDate.of(2026, 1, 1);

    @Test
    void montantAVerserDeduitBonsDepensesEtComplements() {
        // Feuille station : Q = -(J - L - N - P), depenses deduites dans l'ecart (T)
        assertThat(CalculCaisse.montantAVerser(f(1_000_000), f(50_000), f(20_000), f(15_000), f(74_550)))
                .isEqualByComparingTo("840450");
    }

    @Test
    void leSoldeEstReporteJourApresJour() {
        List<CalculCaisse.Ligne> l = CalculCaisse.derouler(f(39_760), List.of(
                new CalculCaisse.Jour(J1, f(500_000), BigDecimal.ZERO, null),
                new CalculCaisse.Jour(J1.plusDays(1), f(450_000), f(500_000), null),
                new CalculCaisse.Jour(J1.plusDays(2), null, f(400_000), null)));
        assertThat(l).extracting(CalculCaisse.Ligne::solde).containsExactly(f(539_760), f(489_760), f(89_760));
        assertThat(l.get(1).ouverture()).isEqualByComparingTo("539760");
        assertThat(l.get(2).montantAVerser()).isNull();   // journee non cloturee : rien a verser encore
    }

    @Test
    void unArreteRemplaceLeSoldeParLesEspecesComptees() {
        List<CalculCaisse.Ligne> l = CalculCaisse.derouler(f(0), List.of(
                new CalculCaisse.Jour(J1, f(300_000), BigDecimal.ZERO, new CalculCaisse.Arrete(false, f(298_500))),
                new CalculCaisse.Jour(J1.plusDays(1), f(200_000), f(298_500), null)));
        assertThat(l.get(0).theorique()).isEqualByComparingTo("300000");
        assertThat(l.get(0).ecart()).isEqualByComparingTo("-1500");
        assertThat(l.get(0).solde()).isEqualByComparingTo("298500");
        assertThat(l.get(1).solde()).isEqualByComparingTo("200000");

        CalculCaisse.Ligne reprise = CalculCaisse.derouler(f(5), List.of(
                new CalculCaisse.Jour(J1, null, BigDecimal.ZERO, new CalculCaisse.Arrete(true, f(603_760))))).get(0);
        assertThat(reprise.ecart()).isNull();
        assertThat(reprise.solde()).isEqualByComparingTo("603760");
    }

    @Test
    void retardQuandLeSoldeDepasseLesRecettesDesDerniersJours() {
        List<BigDecimal> derniers = List.of(f(100_000), f(120_000), f(90_000));
        assertThat(CalculCaisse.retard(f(310_000), derniers, 3)).isFalse();
        assertThat(CalculCaisse.retard(f(310_001), derniers, 3)).isTrue();
        assertThat(CalculCaisse.retard(f(-5_000), derniers, 3)).isFalse();
        assertThat(CalculCaisse.retard(f(900_000), derniers.subList(0, 2), 3)).isFalse();   // historique trop court
    }
}
