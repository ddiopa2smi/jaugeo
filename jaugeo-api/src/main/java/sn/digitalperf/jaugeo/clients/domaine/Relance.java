package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

/** Relance d'un client en retard de paiement : niveau 1 (rappel), 2 (relance), 3 (mise en demeure). */
@Entity
@Table(schema = "clients", name = "relance")
public class Relance extends EntiteAuditee {

    public enum Mode { COURRIER, EMAIL, TELEPHONE, VISITE }

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "date_relance", nullable = false, updatable = false)
    private LocalDate dateRelance;

    @Column(nullable = false, updatable = false)
    private int niveau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Mode mode;

    @Column(name = "montant_echu", nullable = false, updatable = false)
    private BigDecimal montantEchu;

    @Column(updatable = false)
    private String commentaire;

    protected Relance() {
    }

    public Relance(long clientId, LocalDate date, int niveau, Mode mode, BigDecimal montantEchu, String commentaire) {
        this.clientId = clientId;
        this.dateRelance = date;
        this.niveau = niveau;
        this.mode = mode;
        this.montantEchu = montantEchu;
        this.commentaire = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
    }

    public Long getClientId() {
        return clientId;
    }

    public LocalDate getDateRelance() {
        return dateRelance;
    }

    public int getNiveau() {
        return niveau;
    }

    public Mode getMode() {
        return mode;
    }

    public BigDecimal getMontantEchu() {
        return montantEchu;
    }

    public String getCommentaire() {
        return commentaire;
    }
}
