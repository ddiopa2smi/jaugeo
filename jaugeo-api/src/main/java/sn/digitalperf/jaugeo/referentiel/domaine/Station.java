package sn.digitalperf.jaugeo.referentiel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;

@Entity
@Table(schema = "referentiel", name = "station")
public class Station extends EntiteAuditee {

    public enum TypeStation { ROUTIERE, PECHE, MIXTE }

    public enum ModeExploitation { PROPRE, GERANCE_LIBRE, LOCATION_GERANCE }

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_station", nullable = false)
    private TypeStation type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    private String localite;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_exploitation", nullable = false)
    private ModeExploitation modeExploitation;

    @Column(name = "date_ouverture")
    private LocalDate dateOuverture;

    @Column(nullable = false)
    private boolean actif = true;

    protected Station() {
    }

    public Station(String code) {
        this.code = code;
    }

    public void modifier(String nom, TypeStation type, Region region, String localite, BigDecimal latitude,
                         BigDecimal longitude, ModeExploitation modeExploitation, LocalDate dateOuverture,
                         boolean actif) {
        this.nom = nom;
        this.type = type;
        this.region = region;
        this.localite = localite;
        this.latitude = latitude;
        this.longitude = longitude;
        this.modeExploitation = modeExploitation;
        this.dateOuverture = dateOuverture;
        this.actif = actif;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public TypeStation getType() {
        return type;
    }

    public Region getRegion() {
        return region;
    }

    public String getLocalite() {
        return localite;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public ModeExploitation getModeExploitation() {
        return modeExploitation;
    }

    public LocalDate getDateOuverture() {
        return dateOuverture;
    }

    public boolean isActif() {
        return actif;
    }
}
