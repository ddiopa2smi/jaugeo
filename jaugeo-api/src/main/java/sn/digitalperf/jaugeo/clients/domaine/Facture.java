package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Facture periodique d'un client : les bons d'une periode, toutes stations. Echeance = date de facture + delai de
 * paiement du client. Une facture annulee libere ses bons, qui peuvent etre factures a nouveau.
 */
@Entity
@Table(schema = "clients", name = "facture")
public class Facture extends EntiteAuditee {

    public enum Statut { EMISE, ANNULEE }

    @Column(nullable = false, updatable = false)
    private String numero;

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "periode_debut", nullable = false, updatable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", nullable = false, updatable = false)
    private LocalDate periodeFin;

    @Column(name = "date_facture", nullable = false, updatable = false)
    private LocalDate dateFacture;

    @Column(name = "date_echeance", nullable = false, updatable = false)
    private LocalDate dateEcheance;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EMISE;

    @Column(name = "motif_annulation")
    private String motifAnnulation;

    @Column(name = "annulee_par")
    private String annuleePar;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    protected Facture() {
    }

    public Facture(String numero, long clientId, LocalDate periodeDebut, LocalDate periodeFin, LocalDate dateFacture,
                   Integer delaiPaiement, BigDecimal montant) {
        this.numero = numero;
        this.clientId = clientId;
        this.periodeDebut = periodeDebut;
        this.periodeFin = periodeFin;
        this.dateFacture = dateFacture;
        this.dateEcheance = dateFacture.plusDays(delaiPaiement == null ? 0 : delaiPaiement);
        this.montant = montant;
    }

    public void annuler(String motif, String par, Instant le) {
        if (statut == Statut.ANNULEE) {
            throw ErreurMetier.conflit("FACTURE_ANNULEE", "La facture " + numero + " est déjà annulée.");
        }
        if (motif == null || motif.isBlank()) {
            throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Indiquez le motif de l'annulation.");
        }
        statut = Statut.ANNULEE;
        motifAnnulation = motif.trim();
        annuleePar = par;
        annuleeLe = le;
    }

    public String getNumero() {
        return numero;
    }

    public Long getClientId() {
        return clientId;
    }

    public LocalDate getPeriodeDebut() {
        return periodeDebut;
    }

    public LocalDate getPeriodeFin() {
        return periodeFin;
    }

    public LocalDate getDateFacture() {
        return dateFacture;
    }

    public LocalDate getDateEcheance() {
        return dateEcheance;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public Statut getStatut() {
        return statut;
    }

    public String getMotifAnnulation() {
        return motifAnnulation;
    }
}
