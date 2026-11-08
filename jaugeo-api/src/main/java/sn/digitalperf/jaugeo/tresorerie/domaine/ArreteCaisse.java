package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.commun.Libelles;

/**
 * Arrete de caisse a la fin d'une journee : especes comptees chez le gerant. La caisse repart de ce montant ;
 * un controle compare les especes au solde theorique, une reprise fixe le solde de depart.
 */
@Entity
@Table(schema = "tresorerie", name = "arrete_caisse")
public class ArreteCaisse extends EntiteAuditee {

    public enum Type { REPRISE, CONTROLE }

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_jour", nullable = false, updatable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_arrete", nullable = false, updatable = false)
    private Type type;

    @Column(name = "solde_theorique", updatable = false)
    private BigDecimal soldeTheorique;

    @Column(name = "especes_comptees", nullable = false, updatable = false)
    private BigDecimal especesComptees;

    @Column(updatable = false)
    private BigDecimal ecart;

    @Column(updatable = false)
    private String responsable;

    @Column(updatable = false)
    private String observation;

    protected ArreteCaisse() {
    }

    public ArreteCaisse(long stationId, LocalDate dateJour, Type type, BigDecimal soldeTheorique, BigDecimal especesComptees,
                        String responsable, String observation) {
        if (especesComptees == null || especesComptees.signum() < 0 || especesComptees.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Les espèces comptées sont un nombre entier de francs, positif ou nul.");
        }
        this.stationId = stationId;
        this.dateJour = dateJour;
        this.type = type;
        this.especesComptees = especesComptees;
        this.responsable = responsable == null || responsable.isBlank() ? null : responsable.trim();
        this.observation = observation == null || observation.isBlank() ? null : observation.trim();
        if (type == Type.CONTROLE) {
            this.soldeTheorique = soldeTheorique;
            this.ecart = especesComptees.subtract(soldeTheorique);
            if (ecart.signum() != 0 && this.observation == null) {
                throw ErreurMetier.regle("OBSERVATION_OBLIGATOIRE", "Écart de " + Libelles.francs(ecart)
                        + " entre les espèces comptées et le solde théorique de " + Libelles.francs(soldeTheorique)
                        + " : une observation est obligatoire.");
            }
        }
    }

    public boolean manquant() {
        return ecart != null && ecart.signum() < 0;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getSoldeTheorique() {
        return soldeTheorique;
    }

    public BigDecimal getEspecesComptees() {
        return especesComptees;
    }

    public BigDecimal getEcart() {
        return ecart;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getObservation() {
        return observation;
    }
}
