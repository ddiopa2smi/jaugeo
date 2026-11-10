package sn.digitalperf.jaugeo.reporting.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import sn.digitalperf.jaugeo.reporting.domaine.CalculMarge.VenteDuJour;

class CalculMargeTest {

    private static final LocalDate J1 = LocalDate.of(2026, 3, 1);

    private static NavigableMap<LocalDate, BigDecimal> prix(Object... datesEtPrix) {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < datesEtPrix.length; i += 2) {
            m.put((LocalDate) datesEtPrix[i], new BigDecimal(datesEtPrix[i + 1].toString()));
        }
        return m;
    }

    private static VenteDuJour vente(int jour, String litres) {
        return new VenteDuJour(J1.plusDays(jour - 1), new BigDecimal(litres));
    }

    @Test
    void chaqueJourEstValoriseAuPrixEnVigueurCeJourLa() {
        // 600 F jusqu'au 9 mars, 640 F a partir du 10
        var prix = prix(LocalDate.of(2026, 1, 1), 600, LocalDate.of(2026, 3, 10), 640);
        BigDecimal cout = CalculMarge.coutDesVentes(List.of(vente(9, "1000"), vente(10, "500.5"), vente(31, "100")), prix);
        // 1000 x 600 + 500,5 x 640 + 100 x 640
        assertThat(cout).isEqualByComparingTo("984320");
    }

    @Test
    void unJourDeVenteSansPrixRendLeCoutInconnu() {
        var prix = prix(LocalDate.of(2026, 3, 5), 600);
        assertThat(CalculMarge.coutDesVentes(List.of(vente(4, "100"), vente(5, "100")), prix)).isNull();
        // Un jour sans vente n'a pas besoin de prix
        assertThat(CalculMarge.coutDesVentes(List.of(vente(4, "0"), vente(5, "100")), prix)).isEqualByComparingTo("60000");
        assertThat(CalculMarge.coutDesVentes(List.of(), prix())).isEqualByComparingTo("0");
    }

    @Test
    void lEcartDeStockEstValoriseAuPrixDeFinDeMois() {
        var prix = prix(LocalDate.of(2026, 1, 1), 600, LocalDate.of(2026, 3, 10), 640);
        LocalDate fin = LocalDate.of(2026, 3, 31);
        assertThat(CalculMarge.valeurEcart(new BigDecimal("-120.5"), prix, fin)).isEqualByComparingTo("-77120");
        assertThat(CalculMarge.valeurEcart(BigDecimal.ZERO, prix(), fin)).isEqualByComparingTo("0");
        assertThat(CalculMarge.valeurEcart(new BigDecimal("-10"), prix(), fin)).isNull();
    }

    @Test
    void leTauxEstNulSansBaseOuSansValeur() {
        assertThat(CalculMarge.taux(new BigDecimal("50000"), new BigDecimal("350000"))).isEqualByComparingTo("14.29");
        assertThat(CalculMarge.taux(null, new BigDecimal("350000"))).isNull();
        assertThat(CalculMarge.taux(new BigDecimal("1"), BigDecimal.ZERO)).isNull();
    }
}
