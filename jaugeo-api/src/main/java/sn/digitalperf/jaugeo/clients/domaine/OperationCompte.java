package sn.digitalperf.jaugeo.clients.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Operation sur le compte d'un client a une station : reprise d'une creance existante au demarrage du suivi, ou
 * reglement. Un reglement en especes a la station est saisi dans la journee (il entre dans la caisse du gerant) ;
 * les autres modes sont encaisses par le siege.
 */
@Entity
@Table(schema = "clients", name = "operation_compte")
public class OperationCompte extends EntiteAuditee {

    public enum Type { REPRISE, REGLEMENT }

    public enum Mode { ESPECES_STATION, ESPECES_SIEGE, VIREMENT, CHEQUE, COMPENSATION }

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "station_id", nullable = false, updatable = false)
    private Long stationId;

    @Column(name = "date_operation", nullable = false, updatable = false)
    private LocalDate dateOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation", nullable = false, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Mode mode;

    @Column(name = "journee_id", updatable = false)
    private Long journeeId;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Column(updatable = false)
    private String reference;

    @Column(updatable = false)
    private String observation;

    protected OperationCompte() {
    }

    private OperationCompte(long clientId, long stationId, LocalDate date, Type type, Mode mode, Long journeeId,
                            BigDecimal montant, String reference, String observation) {
        if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > 0) {
            throw ErreurMetier.regle("MONTANT_INVALIDE", "Le montant est un nombre entier de francs, supérieur à zéro.");
        }
        if (date == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "Indiquez la date de l'opération.");
        }
        this.clientId = clientId;
        this.stationId = stationId;
        this.dateOperation = date;
        this.type = type;
        this.mode = mode;
        this.journeeId = journeeId;
        this.montant = montant;
        this.reference = reference == null || reference.isBlank() ? null : reference.trim();
        this.observation = observation == null || observation.isBlank() ? null : observation.trim();
    }

    public static OperationCompte reprise(long clientId, long stationId, LocalDate date, BigDecimal montant, String reference,
                                          String observation) {
        return new OperationCompte(clientId, stationId, date, Type.REPRISE, null, null, montant, reference, observation);
    }

    /** Reglement encaisse par le siege (tout mode sauf les especes de la station). */
    public static OperationCompte reglementSiege(long clientId, long stationId, LocalDate date, Mode mode, BigDecimal montant,
                                                 String reference, String observation) {
        if (mode == null || mode == Mode.ESPECES_STATION) {
            throw ErreurMetier.regle("MODE_INVALIDE", "Les espèces remises au gérant se saisissent dans la journée de la station.");
        }
        return new OperationCompte(clientId, stationId, date, Type.REGLEMENT, mode, null, montant, reference, observation);
    }

    /** Recouvrement en especes par le gerant, saisi dans la journee. */
    public static OperationCompte recouvrementStation(long clientId, long stationId, LocalDate date, long journeeId,
                                                      BigDecimal montant, String reference, String observation) {
        return new OperationCompte(clientId, stationId, date, Type.REGLEMENT, Mode.ESPECES_STATION, journeeId, montant,
                reference, observation);
    }

    /** Signe sur la creance : une reprise l'augmente, un reglement la diminue. */
    public BigDecimal effet() {
        return type == Type.REPRISE ? montant : montant.negate();
    }

    public Long getClientId() {
        return clientId;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getDateOperation() {
        return dateOperation;
    }

    public Type getType() {
        return type;
    }

    public Mode getMode() {
        return mode;
    }

    public Long getJourneeId() {
        return journeeId;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public String getReference() {
        return reference;
    }

    public String getObservation() {
        return observation;
    }
}
