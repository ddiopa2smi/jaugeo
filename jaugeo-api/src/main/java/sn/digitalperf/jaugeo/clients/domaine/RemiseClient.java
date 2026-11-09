package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Prix contractuel d'un client : remise en francs par litre sur le prix a la pompe d'un produit, a partir d'une date.
 * La remise en vigueur a la date d'un bon est la plus recente commencee ; une remise nulle met fin a la precedente.
 */
@Entity
@Table(schema = "clients", name = "remise_client")
public class RemiseClient extends EntiteAuditee {

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false, updatable = false)
    private LocalDate debut;

    @Column(nullable = false, updatable = false)
    private BigDecimal remise;

    protected RemiseClient() {
    }

    public RemiseClient(long clientId, long produitId, LocalDate debut, BigDecimal remise) {
        if (debut == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date d'effet de la remise.");
        }
        if (remise == null || remise.signum() < 0 || remise.stripTrailingZeros().scale() > 2) {
            throw ErreurMetier.regle("REMISE_INVALIDE", "La remise au litre est positive ou nulle, avec deux décimales au plus.");
        }
        this.clientId = clientId;
        this.produitId = produitId;
        this.debut = debut;
        this.remise = remise;
    }

    public Long getClientId() {
        return clientId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public LocalDate getDebut() {
        return debut;
    }

    public BigDecimal getRemise() {
        return remise;
    }
}
