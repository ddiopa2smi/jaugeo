package sn.digitalperf.jaugeo.approvisionnement.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/** Camion-citerne d'un transporteur : sa capacite borne la quantite commandee d'une tournee. */
@Entity
@Table(schema = "approvisionnement", name = "citerne")
public class Citerne extends EntiteAuditee {

    @Column(name = "transporteur_id", nullable = false, updatable = false)
    private Long transporteurId;

    @Column(nullable = false)
    private String immatriculation;

    @Column(name = "capacite_litres", nullable = false)
    private BigDecimal capaciteLitres;

    @Column(nullable = false)
    private boolean actif = true;

    protected Citerne() {
    }

    public Citerne(long transporteurId) {
        this.transporteurId = transporteurId;
    }

    public void renseigner(String immatriculation, BigDecimal capaciteLitres, Boolean actif) {
        if (immatriculation == null || immatriculation.isBlank()) {
            throw ErreurMetier.regle("IMMATRICULATION_OBLIGATOIRE", "Indiquez l'immatriculation de la citerne.");
        }
        if (capaciteLitres == null || capaciteLitres.signum() <= 0) {
            throw ErreurMetier.regle("CAPACITE_INVALIDE", "Indiquez la capacité de la citerne en litres.");
        }
        this.immatriculation = Textes.majuscules(immatriculation);
        this.capaciteLitres = capaciteLitres;
        this.actif = actif == null || actif;
    }

    public Long getTransporteurId() {
        return transporteurId;
    }

    public String getImmatriculation() {
        return immatriculation;
    }

    public BigDecimal getCapaciteLitres() {
        return capaciteLitres;
    }

    public boolean isActif() {
        return actif;
    }
}
