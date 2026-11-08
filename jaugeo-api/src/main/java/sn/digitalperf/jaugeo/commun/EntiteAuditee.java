package sn.digitalperf.jaugeo.commun;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base des entites metier : cle technique, identifiant public UUID v7,
 * verrouillage optimiste et colonnes d'audit (qui, quand).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntiteAuditee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Version
    @Column(nullable = false)
    private Integer version;

    @CreatedDate
    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @CreatedBy
    @Column(name = "cree_par", nullable = false, updatable = false)
    private String creePar;

    @LastModifiedDate
    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @LastModifiedBy
    @Column(name = "modifie_par", nullable = false)
    private String modifiePar;

    @PrePersist
    void attribuerUuid() {
        if (uuid == null) {
            uuid = Uuids.v7();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public String getCreePar() {
        return creePar;
    }

    public Instant getModifieLe() {
        return modifieLe;
    }

    public String getModifiePar() {
        return modifiePar;
    }

    /** Refuse une modification faite sur une version perimee de l'objet. */
    public void verifierVersion(Integer versionAttendue) {
        if (versionAttendue != null && !versionAttendue.equals(version)) {
            throw ErreurMetier.conflit("VERSION_OBSOLETE",
                    "Cet élément a été modifié entre-temps par un autre utilisateur. Rechargez-le avant de modifier.");
        }
    }
}
