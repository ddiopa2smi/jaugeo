package sn.digitalperf.jaugeo.tarification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariationPrixTest {

    @ParameterizedTest(name = "{0} -> {1} : a confirmer = {2}")
    @CsvSource({
            "680, 755, false",   // hausse reelle d'aout 2026 (+11 %)
            "920, 990, false",
            "755, 906, false",   // +20 % tout juste
            "755, 907, true",
            "755, 7550, true",   // zero de trop
            "755, 75.5, true",   // virgule mal placee
            "497, 397, true"})   // -20,1 %
    void signaleLesVariationsDePlusDeVingtPourCent(String ancien, String nouveau, boolean aConfirmer) {
        assertThat(PrixService.variationExcessive(new BigDecimal(ancien), new BigDecimal(nouveau))).isEqualTo(aConfirmer);
    }
}
