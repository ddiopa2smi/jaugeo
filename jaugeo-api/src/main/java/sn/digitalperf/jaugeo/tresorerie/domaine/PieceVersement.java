package sn.digitalperf.jaugeo.tresorerie.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import sn.digitalperf.jaugeo.commun.PieceJointe;

/** Photo (ou scan PDF) du bordereau de versement. */
@Entity
@Table(schema = "tresorerie", name = "piece_versement")
public class PieceVersement {

    /** Description sans le contenu, pour les listes. */
    public interface Description {
        Long getVersementId();

        String getNomFichier();

        String getTypeMime();

        Integer getTaille();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "versement_id", nullable = false, updatable = false)
    private Long versementId;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(nullable = false)
    private Integer taille;

    @Column(nullable = false)
    private String empreinte;

    @Column(nullable = false)
    private byte[] contenu;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "cree_par", nullable = false)
    private String creePar;

    protected PieceVersement() {
    }

    public PieceVersement(long versementId, PieceJointe piece, String par, Instant le) {
        this.versementId = versementId;
        this.nomFichier = piece.nomFichier();
        this.typeMime = piece.typeMime();
        this.taille = piece.taille();
        this.empreinte = piece.empreinte();
        this.contenu = piece.contenu();
        this.creePar = par;
        this.creeLe = le;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public byte[] getContenu() {
        return contenu;
    }
}
