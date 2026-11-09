package sn.digitalperf.jaugeo.stock.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Facteurs de correction a 15 °C compares aux valeurs publiees de la table ASTM 54B (4 decimales). */
class Correction15Test {

    private static BigDecimal vcf(String t, String densite) {
        return Correction15.facteur(new BigDecimal(t), new BigDecimal(densite)).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    @Test
    void facteursDeLaTable54B() {
        assertThat(vcf("15", "0.840")).isEqualByComparingTo("1.0000");
        assertThat(vcf("30", "0.840")).isEqualByComparingTo("0.9873");   // gasoil
        assertThat(vcf("35", "0.840")).isEqualByComparingTo("0.9830");
        assertThat(vcf("10", "0.840")).isEqualByComparingTo("1.0042");
        assertThat(vcf("30", "0.745")).isEqualByComparingTo("0.9817");   // super
        assertThat(vcf("30", "0.800")).isEqualByComparingTo("0.9860");   // kerosene
        assertThat(vcf("30", "0.780")).isEqualByComparingTo("0.9843");   // zone de transition
    }

    @Test
    void volumeA15EtDomaine() {
        assertThat(Correction15.volume15(new BigDecimal("20000"), new BigDecimal("0.98730"))).isEqualByComparingTo("19746.00");
        assertThatThrownBy(() -> Correction15.facteur(BigDecimal.TEN, new BigDecimal("0.55")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autonomieEtReapprovisionnement() {
        BigDecimal a = CalculStock.autonomieJours(new BigDecimal("28600"), new BigDecimal("500"), new BigDecimal("550"));
        assertThat(a).isEqualByComparingTo("51.1");
        assertThat(CalculStock.autonomieJours(new BigDecimal("300"), new BigDecimal("500"), new BigDecimal("550"))).isEqualByComparingTo("0");
        assertThat(CalculStock.autonomieJours(new BigDecimal("300"), null, BigDecimal.ZERO)).isNull();
        // Seuil en litres prioritaire ; a defaut, autonomie sous le nombre de jours d'alerte
        assertThat(CalculStock.aReapprovisionner(new BigDecimal("28600"), new BigDecimal("29000"), a, new BigDecimal("3"))).isTrue();
        assertThat(CalculStock.aReapprovisionner(new BigDecimal("28600"), null, a, new BigDecimal("3"))).isFalse();
        assertThat(CalculStock.aReapprovisionner(new BigDecimal("1000"), null, new BigDecimal("2.5"), new BigDecimal("3"))).isTrue();
    }
}
