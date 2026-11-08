package sn.digitalperf.jaugeo.securite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

class PolitiqueMotDePasseTest {

    @ParameterizedTest
    @ValueSource(strings = {"court1A", "toutenminuscule1", "TOUTENMAJUSCULE1", "SansChiffreIci"})
    void refuseLesMotsDePasseFaibles(String mdp) {
        assertThatThrownBy(() -> PolitiqueMotDePasse.verifier(mdp, "kmbaye"))
                .isInstanceOf(ErreurMetier.class)
                .extracting("code").isEqualTo("MOT_DE_PASSE_FAIBLE");
    }

    @Test
    void refuseUnMotDePasseContenantLIdentifiant() {
        assertThatThrownBy(() -> PolitiqueMotDePasse.verifier("Kmbaye-2026-ok", "kmbaye"))
                .isInstanceOf(ErreurMetier.class);
    }

    @Test
    void accepteUnMotDePasseRobuste() {
        assertThatCode(() -> PolitiqueMotDePasse.verifier("Station-Yoff-2026", "kmbaye")).doesNotThrowAnyException();
    }

    @RepeatedTest(50)
    void leMotDePasseProvisoireRespecteLaPolitique() {
        String provisoire = PolitiqueMotDePasse.genererProvisoire();
        assertThat(provisoire).hasSize(12);
        assertThatCode(() -> PolitiqueMotDePasse.verifier(provisoire, "utilisateur")).doesNotThrowAnyException();
    }
}
