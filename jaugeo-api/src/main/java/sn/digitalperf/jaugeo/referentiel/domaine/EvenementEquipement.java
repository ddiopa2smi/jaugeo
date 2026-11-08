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
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import sn.digitalperf.jaugeo.commun.EntiteAuditee;
import sn.digitalperf.jaugeo.commun.ErreurMetier;

/**
 * Evenement de la vie d'un equipement (pistolet, pompe ou cuve). Historique en insertion seule :
 * une erreur se corrige par un nouvel evenement, jamais par modification.
 */
@Entity
@Table(schema = "referentiel", name = "evenement_equipement")
public class EvenementEquipement extends EntiteAuditee {

    public enum Type {
        REMPLACEMENT_TOTALISATEUR, REMISE_A_ZERO, ETALONNAGE, POSE_SCELLES, NETTOYAGE, MAINTENANCE, AUTRE;

        public boolean changeCompteur() {
            return this == REMPLACEMENT_TOTALISATEUR || this == REMISE_A_ZERO;
        }
    }

    public enum Cible {
        PISTOLET(EnumSet.of(Type.REMPLACEMENT_TOTALISATEUR, Type.REMISE_A_ZERO, Type.ETALONNAGE, Type.POSE_SCELLES,
                Type.MAINTENANCE, Type.AUTRE)),
        POMPE(EnumSet.of(Type.ETALONNAGE, Type.POSE_SCELLES, Type.MAINTENANCE, Type.AUTRE)),
        CUVE(EnumSet.of(Type.ETALONNAGE, Type.NETTOYAGE, Type.MAINTENANCE, Type.AUTRE));

        private final Set<Type> typesAutorises;

        Cible(Set<Type> typesAutorises) {
            this.typesAutorises = typesAutorises;
        }

        public Set<Type> typesAutorises() {
            return typesAutorises;
        }
    }

    /** Donnees saisies, communes a toutes les cibles. */
    public record Saisie(Type type, Instant date, BigDecimal indexAvant, BigDecimal indexApres,
                         String referenceScelles, String intervenant, LocalDate prochaineVerification,
                         String commentaire) {
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false, updatable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pistolet_id", updatable = false)
    private Pistolet pistolet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pompe_id", updatable = false)
    private Pompe pompe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuve_id", updatable = false)
    private Cuve cuve;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_evenement", nullable = false, updatable = false)
    private Type type;

    @Column(name = "date_evenement", nullable = false, updatable = false)
    private Instant date;

    @Column(name = "index_avant", updatable = false)
    private BigDecimal indexAvant;

    @Column(name = "index_apres", updatable = false)
    private BigDecimal indexApres;

    @Column(name = "reference_scelles", updatable = false)
    private String referenceScelles;

    @Column(updatable = false)
    private String intervenant;

    @Column(name = "prochaine_verification", updatable = false)
    private LocalDate prochaineVerification;

    @Column(updatable = false)
    private String commentaire;

    protected EvenementEquipement() {
    }

    private EvenementEquipement(Station station, Cible cible, Saisie s, Instant maintenant) {
        if (s.type() == null || !cible.typesAutorises().contains(s.type())) {
            throw ErreurMetier.regle("TYPE_EVENEMENT_INVALIDE",
                    "Cet événement ne s'applique pas à ce type d'équipement.");
        }
        if (s.date() == null) {
            throw ErreurMetier.regle("DATE_OBLIGATOIRE", "La date de l'événement est obligatoire.");
        }
        if (s.date().isAfter(maintenant.plusSeconds(300))) {
            throw ErreurMetier.regle("EVENEMENT_FUTUR", "Un événement ne peut pas être daté dans le futur.");
        }
        if (s.prochaineVerification() != null
                && !s.prochaineVerification().isAfter(s.date().atZone(java.time.ZoneOffset.UTC).toLocalDate())) {
            throw ErreurMetier.regle("VERIFICATION_ANTERIEURE",
                    "La prochaine vérification doit être postérieure à l'événement.");
        }
        if (s.type() == Type.POSE_SCELLES && vide(s.referenceScelles())) {
            throw ErreurMetier.regle("SCELLES_OBLIGATOIRES", "Indiquez les numéros des scellés posés.");
        }
        if (s.type() == Type.ETALONNAGE && vide(s.intervenant())) {
            throw ErreurMetier.regle("INTERVENANT_OBLIGATOIRE", "Indiquez l'organisme ou le technicien ayant étalonné.");
        }
        if (s.type().changeCompteur()) {
            if (s.indexAvant() == null || s.indexApres() == null) {
                throw ErreurMetier.regle("INDEX_OBLIGATOIRES",
                        "Relevez l'index de l'ancien compteur et celui du nouveau compteur.");
            }
            if (vide(s.commentaire())) {
                throw ErreurMetier.regle("MOTIF_OBLIGATOIRE", "Expliquez le motif du changement de compteur.");
            }
            if (s.type() == Type.REMISE_A_ZERO && s.indexApres().compareTo(s.indexAvant()) >= 0) {
                throw ErreurMetier.regle("REMISE_A_ZERO_INCOHERENTE",
                        "Après une remise à zéro, l'index doit être inférieur à l'index relevé avant.");
            }
        } else if (s.indexAvant() != null || s.indexApres() != null) {
            throw ErreurMetier.regle("INDEX_INATTENDUS", "Les index ne concernent que les changements de compteur.");
        }
        this.station = station;
        this.type = s.type();
        this.date = s.date();
        this.indexAvant = s.indexAvant();
        this.indexApres = s.indexApres();
        this.referenceScelles = nettoyer(s.referenceScelles());
        this.intervenant = nettoyer(s.intervenant());
        this.prochaineVerification = s.prochaineVerification();
        this.commentaire = nettoyer(s.commentaire());
    }

    /**
     * Evenement sur un pistolet. Pour un changement de compteur, l'index releve sur l'ancien compteur
     * ne peut pas etre inferieur a l'index de reference (mise en service ou dernier changement), et
     * l'evenement doit etre posterieur au dernier changement de compteur : la chaine des compteurs
     * reste ainsi exploitable pour le calcul des ventes.
     */
    public static EvenementEquipement surPistolet(Pistolet pistolet, Saisie s, EvenementEquipement dernierChangement,
                                                  Instant maintenant) {
        if (s.type() != null && s.type().changeCompteur()) {
            BigDecimal reference = dernierChangement == null
                    ? pistolet.getIndexMiseEnService() : dernierChangement.getIndexApres();
            if (dernierChangement != null && s.date() != null && !s.date().isAfter(dernierChangement.getDate())) {
                throw ErreurMetier.regle("EVENEMENT_ANTERIEUR",
                        "Un changement de compteur plus récent est déjà enregistré pour ce pistolet.");
            }
            if (s.indexAvant() != null && s.indexAvant().compareTo(reference) < 0) {
                throw ErreurMetier.regle("INDEX_AVANT_INCOHERENT", "L'index relevé sur l'ancien compteur ("
                        + s.indexAvant().stripTrailingZeros().toPlainString() + ") est inférieur à l'index de référence ("
                        + reference.stripTrailingZeros().toPlainString() + ").");
            }
        }
        EvenementEquipement e = new EvenementEquipement(pistolet.getPompe().getStation(), Cible.PISTOLET, s, maintenant);
        e.pistolet = pistolet;
        return e;
    }

    public static EvenementEquipement surPompe(Pompe pompe, Saisie s, Instant maintenant) {
        EvenementEquipement e = new EvenementEquipement(pompe.getStation(), Cible.POMPE, s, maintenant);
        e.pompe = pompe;
        return e;
    }

    public static EvenementEquipement surCuve(Cuve cuve, Saisie s, Instant maintenant) {
        EvenementEquipement e = new EvenementEquipement(cuve.getStation(), Cible.CUVE, s, maintenant);
        e.cuve = cuve;
        return e;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static String nettoyer(String s) {
        return vide(s) ? null : s.trim();
    }

    public Station getStation() {
        return station;
    }

    public Pistolet getPistolet() {
        return pistolet;
    }

    public Pompe getPompe() {
        return pompe;
    }

    public Cuve getCuve() {
        return cuve;
    }

    public Type getType() {
        return type;
    }

    public Instant getDate() {
        return date;
    }

    public BigDecimal getIndexAvant() {
        return indexAvant;
    }

    public BigDecimal getIndexApres() {
        return indexApres;
    }

    public String getReferenceScelles() {
        return referenceScelles;
    }

    public String getIntervenant() {
        return intervenant;
    }

    public LocalDate getProchaineVerification() {
        return prochaineVerification;
    }

    public String getCommentaire() {
        return commentaire;
    }
}
