package sn.digitalperf.jaugeo.stock.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import sn.digitalperf.jaugeo.stock.domaine.CalculStock.Niveau;

class CalculStockTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");
    private static final BigDecimal ENQUETE = new BigDecimal("2");

    private static CalculStock.Resultat calcul(String depart, String recu, String sorties, String physique,
                                               String cumulSorties, String cumulEcart) {
        return CalculStock.calculer(new CalculStock.Entree(d(depart), d(recu), d(sorties), d(physique), d(cumulSorties),
                d(cumulEcart), new BigDecimal("680"), TOLERANCE, ENQUETE));
    }

    private static BigDecimal d(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    @Test
    void theoriqueEcartEtValeurComme1erJanvierANiandane() {
        // Classeur, Niandane Gasoil le 01/01 : depart 10 900, sorties 419, jauge 10 450
        CalculStock.Resultat r = calcul("10900", "0", "419", "10450", "0", "0");
        assertThat(r.theorique()).isEqualByComparingTo("10481");
        assertThat(r.ecart()).isEqualByComparingTo("-31");
        assertThat(r.ecartValeur()).isEqualByComparingTo("-21080");
        assertThat(r.ecartPctJour()).isEqualByComparingTo("-7.40");
        assertThat(r.niveau()).isEqualTo(Niveau.ENQUETE);
    }

    @Test
    void leNiveauSeJugeSurLeCumulDuMois() {
        // 20 jours a 400 L sans ecart, puis -30 L : -30 / 8 400 = -0,36 % -> dans la tolerance
        CalculStock.Resultat r = calcul("5000", "0", "400", "4570", "8000", "0");
        assertThat(r.ecart()).isEqualByComparingTo("-30");
        assertThat(r.cumulSorties()).isEqualByComparingTo("8400");
        assertThat(r.ecartPctMois()).isEqualByComparingTo("-0.36");
        assertThat(r.niveau()).isEqualTo(Niveau.NORMAL);
        assertThat(r.horsTolerance()).isFalse();

        // Le mois derive : -100 L de plus -> -1,54 %, alerte superviseur
        CalculStock.Resultat alerte = calcul("4570", "0", "400", "4070", "8400", "-30");
        assertThat(alerte.ecartPctMois()).isEqualByComparingTo("-1.48");
        assertThat(alerte.niveau()).isEqualTo(Niveau.ALERTE);

        // Un excedent compte autant qu'un manquant : +100 / 8 800 = 1,14 % (alerte), +200 = 2,27 % (enquete)
        assertThat(calcul("4000", "0", "400", "3700", "8400", "0").niveau()).isEqualTo(Niveau.ALERTE);
        CalculStock.Resultat enquete = calcul("4000", "0", "400", "3800", "8400", "0");
        assertThat(enquete.ecartPctMois()).isEqualByComparingTo("2.27");
        assertThat(enquete.niveau()).isEqualTo(Niveau.ENQUETE);
    }

    @Test
    void lesReceptionsEntrentEnStock() {
        CalculStock.Resultat r = calcul("100", "30000", "8300", "21700", "0", "0");
        assertThat(r.theorique()).isEqualByComparingTo("21800");
        assertThat(r.ecart()).isEqualByComparingTo("-100");
    }

    @Test
    void unStockTheoriqueNegatifSignaleUneReceptionManquante() {
        // Hydrobase le 07/01 : la reception n'avait pas ete saisie le bon jour
        assertThat(calcul("100", "0", "2707", "11800", "0", "0").theorique()).isEqualByComparingTo("-2607");
    }

    @Test
    void sansSortieToutEcartEstAnormal() {
        assertThat(calcul("500", "0", "0", "500", "0", "0").niveau()).isEqualTo(Niveau.NORMAL);
        CalculStock.Resultat fuite = calcul("500", "0", "0", "450", "0", "0");
        assertThat(fuite.ecartPctMois()).isNull();
        assertThat(fuite.niveau()).isEqualTo(Niveau.ALERTE);
    }

    @Test
    void lesDonneesManquantesLaissentLeCalculEnSuspens() {
        CalculStock.Resultat sansJauge = calcul("500", "0", "100", null, "0", "0");
        assertThat(sansJauge.theorique()).isEqualByComparingTo("400");
        assertThat(sansJauge.ecart()).isNull();
        assertThat(sansJauge.niveau()).isNull();
        assertThat(calcul(null, "0", "100", "400", "0", "0").theorique()).isNull();
        assertThat(calcul("500", "0", null, "400", "0", "0").theorique()).isNull();
    }

    @Test
    void quantiteRecueEtComplementCiterne() {
        BigDecimal bl = new BigDecimal("30000");
        assertThat(CalculStock.quantiteRecue(null, d("9900"), d("39780"), bl)).isEqualByComparingTo("29880");
        assertThat(CalculStock.quantiteRecue(d("29950"), d("9900"), d("39780"), bl)).isEqualByComparingTo("29950");
        assertThat(CalculStock.quantiteRecue(null, null, null, bl)).isEqualByComparingTo("30000");

        BigDecimal tolerance = new BigDecimal("0.3");   // 90 L sur 30 000
        assertThat(CalculStock.complement(bl, d("29910"), tolerance)).isZero();
        assertThat(CalculStock.complement(bl, d("29880"), tolerance)).isEqualByComparingTo("120");
        assertThat(CalculStock.complement(bl, d("30050"), tolerance)).isZero();
        assertThat(CalculStock.pourcentageLivraison(bl, d("29880"))).isEqualByComparingTo("-0.40");
    }
}
