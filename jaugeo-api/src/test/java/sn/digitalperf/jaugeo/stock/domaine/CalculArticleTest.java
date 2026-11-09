package sn.digitalperf.jaugeo.stock.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculArticleTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void premiere_entree_fixe_le_cmup_au_cout_d_achat() {
        CalculArticle.Etat e = CalculArticle.entree(new CalculArticle.Etat(BigDecimal.ZERO, BigDecimal.ZERO), d("24"), d("3500"));
        assertThat(e.quantite()).isEqualByComparingTo("24");
        assertThat(e.cmup()).isEqualByComparingTo("3500");
        assertThat(e.valeur()).isEqualByComparingTo("84000");
    }

    @Test
    void entree_suivante_pondere_le_cmup() {
        // 10 a 3 500 + 30 a 3 900 = 152 000 pour 40 : 3 800
        CalculArticle.Etat e = CalculArticle.entree(new CalculArticle.Etat(d("10"), d("3500")), d("30"), d("3900"));
        assertThat(e.quantite()).isEqualByComparingTo("40");
        assertThat(e.cmup()).isEqualByComparingTo("3800");
    }

    @Test
    void cmup_arrondi_au_centime() {
        // 1 a 1 000 + 2 a 1 001 = 3 002 / 3 = 1 000,67
        CalculArticle.Etat e = CalculArticle.entree(new CalculArticle.Etat(d("1"), d("1000")), d("2"), d("1001"));
        assertThat(e.cmup()).isEqualByComparingTo("1000.67");
    }

    @Test
    void sortie_garde_le_cmup_et_refuse_un_stock_negatif() {
        CalculArticle.Etat e = new CalculArticle.Etat(d("40"), d("3800"));
        CalculArticle.Etat apres = CalculArticle.sortie(e, d("15"));
        assertThat(apres.quantite()).isEqualByComparingTo("25");
        assertThat(apres.cmup()).isEqualByComparingTo("3800");
        assertThat(CalculArticle.sortie(e, d("40.5"))).isNull();
        assertThat(CalculArticle.sortie(e, d("40")).quantite()).isZero();
    }

    @Test
    void annulation_d_une_sortie_au_cmup_de_sortie_retrouve_l_etat() {
        CalculArticle.Etat e = new CalculArticle.Etat(d("40"), d("3800"));
        CalculArticle.Etat apres = CalculArticle.entree(CalculArticle.sortie(e, d("15")), d("15"), d("3800"));
        assertThat(apres.quantite()).isEqualByComparingTo("40");
        assertThat(apres.cmup()).isEqualByComparingTo("3800");
    }

    @Test
    void inventaire_fixe_la_quantite_sans_toucher_le_cmup() {
        CalculArticle.Etat e = CalculArticle.inventaire(new CalculArticle.Etat(d("25"), d("3800")), d("23"));
        assertThat(e.quantite()).isEqualByComparingTo("23");
        assertThat(e.cmup()).isEqualByComparingTo("3800");
    }

    @Test
    void comptage_valorise_l_ecart_et_exige_une_observation() {
        CalculArticle.Etat avant = new CalculArticle.Etat(d("25"), d("3800"));
        ComptageArticle c = new ComptageArticle(1, 2, java.time.LocalDate.of(2026, 11, 9), avant, d("23"), "Deux bidons percés", "Huile 5L");
        assertThat(c.getEcart()).isEqualByComparingTo("-2");
        assertThat(c.getEcartValeur()).isEqualByComparingTo("-7600");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new ComptageArticle(1, 2, java.time.LocalDate.of(2026, 11, 9), avant, d("23"), " ", "Huile 5L"))
                .hasMessageContaining("Expliquez l'écart");
        assertThat(new ComptageArticle(1, 2, java.time.LocalDate.of(2026, 11, 9), avant, d("25"), null, "Huile 5L").getEcartValeur())
                .isZero();
    }
}
