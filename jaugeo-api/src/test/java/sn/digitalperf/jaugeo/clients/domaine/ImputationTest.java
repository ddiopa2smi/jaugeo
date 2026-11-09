package sn.digitalperf.jaugeo.clients.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Dette;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Nature;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Tranches;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Imputation des reglements (plus ancien d'abord), balance agee et cartes prepayees. */
class ImputationTest {

    private static BigDecimal f(long v) {
        return BigDecimal.valueOf(v);
    }

    private static final LocalDate J1 = LocalDate.of(2026, 1, 1);

    @Test
    void reglementsSoldentLesDettesLesPlusAnciennesDAbord() {
        List<Dette> dettes = List.of(
                new Dette(Nature.NON_FACTURE, "B-9", LocalDate.of(2026, 3, 2), null, f(10_000), null),
                new Dette(Nature.FACTURE, "FA2026-00002", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 3), f(80_000), 2L),
                new Dette(Nature.REPRISE, "Reprise", J1, J1.plusDays(30), f(50_000), null));
        List<Reste> r = Imputation.imputer(dettes, f(70_000));
        assertThat(r).extracting(x -> x.dette().reference()).containsExactly("Reprise", "FA2026-00002", "B-9");
        assertThat(r).extracting(Reste::reste).containsExactly(f(0), f(60_000), f(10_000));

        // Au 15/04 : la facture est echue (echeance 03/03) et agee de 73 jours ; le bon non facture n'est pas exigible
        LocalDate au = LocalDate.of(2026, 4, 15);
        assertThat(Imputation.echu(r, au)).isEqualByComparingTo("60000");
        assertThat(Imputation.premiereEchue(r, au).dette().reference()).isEqualTo("FA2026-00002");
        Tranches t = Imputation.tranches(r, au);
        assertThat(t.nonFacture()).isEqualByComparingTo("10000");
        assertThat(t.jours61a90()).isEqualByComparingTo("60000");
        assertThat(t.jours0a30()).isEqualByComparingTo("0");
        assertThat(t.total()).isEqualByComparingTo("70000");

        // Avant l'echeance, rien n'est echu
        assertThat(Imputation.echu(r, LocalDate.of(2026, 3, 3))).isEqualByComparingTo("0");
        // Un excedent de reglement ne rend aucune dette negative
        assertThat(Imputation.imputer(dettes, f(500_000))).extracting(Reste::reste).containsOnly(f(0));
    }

    @Test
    void numeroDeCarteAvecCleDeLuhn() {
        for (int i = 0; i < 50; i++) {
            String n = Carte.nouveauNumero();
            assertThat(n).matches("JC\\d{10}");
            assertThat(Carte.normaliser(n)).isEqualTo(n);
            assertThat(Carte.normaliser("jaugeo:carte:" + n.toLowerCase())).isEqualTo(n);
            // Une faute de frappe sur un chiffre est detectee
            char c = n.charAt(5);
            String faux = n.substring(0, 5) + (char) ('0' + (c - '0' + 1) % 10) + n.substring(6);
            assertThat(Carte.normaliser(faux)).isNull();
        }
        assertThat(Carte.normaliser("ABC")).isNull();
    }

    @Test
    void carteDebiteeDansLaLimiteDeSonSolde() {
        Carte k = new Carte(Carte.nouveauNumero(), 1L);
        k.modifier("Moussa Fall", "dk-1234-a");
        assertThat(k.getImmatriculation()).isEqualTo("DK-1234-A");
        k.crediter(f(50_000));
        k.debiter(f(19_800));
        assertThat(k.getSolde()).isEqualByComparingTo("30200");
        assertThatThrownBy(() -> k.debiter(f(30_201))).isInstanceOf(ErreurMetier.class).hasMessageContaining("insuffisant");
        assertThatThrownBy(() -> k.changerStatut(Carte.Statut.ANNULEE)).hasMessageContaining("porte encore");
        k.changerStatut(Carte.Statut.BLOQUEE);
        assertThatThrownBy(() -> k.debiter(f(100))).hasMessageContaining("bloquée");
    }

    @Test
    void remiseAuLitreEtStatutDuBon() {
        Client c = new Client("T1");
        c.modifier("Transports test", null, Client.Recouvrement.DIRECTION, null, true, null);
        Bon b = new Bon(1L, 1L, J1);
        // 40 L a 755 F avec 20 F de remise : 29 400 F dus par le client, 30 200 F a la pompe
        b.renseigner(c, "b-1", 1L, f(40), f(755), f(20), " dk-1 ", null);
        assertThat(b.getPrixUnitaire()).isEqualByComparingTo("735");
        assertThat(b.getMontant()).isEqualByComparingTo("29400");
        assertThat(b.getMontantPompe()).isEqualByComparingTo("30200");
        assertThat(b.getImmatriculation()).isEqualTo("DK-1");

        b.controler(List.of("Plafond dépassé."), false);
        assertThat(b.getStatut()).isEqualTo(Bon.Statut.EN_ATTENTE);
        b.decider(false, "Client déjà relancé", "direction.test", java.time.Instant.now());
        assertThat(b.getStatut()).isEqualTo(Bon.Statut.REFUSE);
        assertThat(b.getRecouvrement()).isEqualTo(Client.Recouvrement.GERANT);
        // Correction sans hausse du montant : la decision est conservee
        b.renseigner(c, "b-1", 1L, f(30), f(755), f(20), "dk-1", null);
        b.controler(List.of("Plafond dépassé."), true);
        assertThat(b.getStatut()).isEqualTo(Bon.Statut.REFUSE);
        assertThat(b.getRecouvrement()).isEqualTo(Client.Recouvrement.GERANT);
        assertThatThrownBy(() -> b.decider(true, "x", "d", java.time.Instant.now())).hasMessageContaining("n'attend pas");
    }
}
