package sn.digitalperf.jaugeo.referentiel.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

class BaremeTest {

    private static Bareme.Point p(int mm, String litres) {
        return new Bareme.Point(mm, new BigDecimal(litres));
    }

    // Cuve cylindrique horizontale : la courbe n'est pas lineaire, d'ou un bareme point par point
    private final Bareme bareme = Bareme.valider(List.of(
            p(2000, "19500"), p(0, "0"), p(100, "310.5"), p(500, "3420"), p(1000, "9750"), p(1500, "16080")));

    @ParameterizedTest(name = "{0} mm -> {1} L")
    @CsvSource({
            "0, 0.00",
            "100, 310.50",       // point exact
            "50, 155.25",        // milieu de [0 ; 100]
            "300, 1865.25",      // 310.5 + (3420 - 310.5) * 200/400
            "1234, 12712.44",    // 9750 + (16080 - 9750) * 234/500
            "2000, 19500.00"})
    void interpoleLineairementEntreDeuxPoints(int mm, String attendu) {
        assertThat(bareme.volumePour(mm)).isEqualByComparingTo(attendu);
    }

    @Test
    void trieLesPointsEtExposeLesMaximums() {
        assertThat(bareme.points()).extracting(Bareme.Point::hauteurMm).containsExactly(0, 100, 500, 1000, 1500, 2000);
        assertThat(bareme.hauteurMax()).isEqualTo(2000);
        assertThat(bareme.volumeMax()).isEqualByComparingTo("19500");
    }

    @Test
    void refuseUneHauteurHorsBareme() {
        assertThatThrownBy(() -> bareme.volumePour(2001))
                .isInstanceOf(ErreurMetier.class).extracting("code").isEqualTo("HAUTEUR_HORS_BAREME");
    }

    @Test
    void refuseUnBaremeIncoherent() {
        assertThatThrownBy(() -> Bareme.valider(List.of(p(0, "0"))))
                .extracting("code").isEqualTo("BAREME_TROP_COURT");
        assertThatThrownBy(() -> Bareme.valider(List.of(p(0, "0"), p(10, "5"), p(10, "6"))))
                .extracting("code").isEqualTo("BAREME_HAUTEUR_EN_DOUBLE");
        assertThatThrownBy(() -> Bareme.valider(List.of(p(0, "0"), p(10, "50"), p(20, "40"))))
                .extracting("code").isEqualTo("BAREME_NON_CROISSANT");
        assertThatThrownBy(() -> Bareme.valider(List.of(p(0, "0"), p(10, "-1"))))
                .extracting("code").isEqualTo("BAREME_VALEUR_NEGATIVE");
    }
}
