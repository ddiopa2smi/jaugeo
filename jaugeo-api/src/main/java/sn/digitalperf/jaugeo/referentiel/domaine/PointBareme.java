package sn.digitalperf.jaugeo.referentiel.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Ligne du bareme d'une cuve (le certificat et la version sont portes par la cuve). */
@Entity
@Table(schema = "referentiel", name = "bareme_cuve")
public class PointBareme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuve_id", nullable = false, updatable = false)
    private Long cuveId;

    @Column(name = "hauteur_mm", nullable = false, updatable = false)
    private int hauteurMm;

    @Column(name = "volume_litres", nullable = false, updatable = false)
    private BigDecimal volumeLitres;

    protected PointBareme() {
    }

    public PointBareme(Long cuveId, Bareme.Point point) {
        this.cuveId = cuveId;
        this.hauteurMm = point.hauteurMm();
        this.volumeLitres = point.volumeLitres();
    }

    public Bareme.Point point() {
        return new Bareme.Point(hauteurMm, volumeLitres);
    }

    public Long getCuveId() {
        return cuveId;
    }
}
