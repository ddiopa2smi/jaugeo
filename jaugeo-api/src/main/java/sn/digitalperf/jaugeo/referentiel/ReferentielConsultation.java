package sn.digitalperf.jaugeo.referentiel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interface publique du referentiel pour les autres modules (tarification, exploitation, stock...).
 * Lecture seule ; aucun filtrage par perimetre : c'est au module appelant de l'appliquer.
 */
public interface ReferentielConsultation {

    record StationRef(long id, UUID uuid, String code, String nom, boolean actif, LocalDate dateOuverture) {
    }

    record ProduitRef(long id, UUID uuid, String code, String libelle, String unite, boolean suiviJaugeage,
                      boolean actif) {
    }

    /** Pistolet avec sa pompe, sa cuve et le produit distribue. */
    record PistoletRef(long id, UUID uuid, String code, String libelle, String pompeCode, long cuveId, String cuveCode,
                       long produitId, String produitCode, String produitLibelle, BigDecimal indexMiseEnService,
                       boolean actif) {
    }

    /** Changement de compteur d'un pistolet : index lu sur l'ancien compteur, index de depart du nouveau. */
    record ChangementCompteur(Instant date, BigDecimal indexAvant, BigDecimal indexApres) {
    }

    List<StationRef> stations();

    Optional<StationRef> station(UUID uuid);

    Optional<StationRef> station(long id);

    List<ProduitRef> produits();

    Optional<ProduitRef> produit(String code);

    /** Pour chaque station (id), les produits (id) stockes dans au moins une cuve active. */
    Map<Long, Set<Long>> produitsEnCuve();

    /** Volume (litres) d'une cuve pour une hauteur de jauge, d'apres son bareme. */
    BigDecimal volumeJauge(UUID cuveId, int hauteurMm);

    /** Changements de compteur d'un pistolet sur [debut, fin), dans l'ordre chronologique. */
    List<ChangementCompteur> changementsCompteur(UUID pistoletId, Instant debut, Instant fin);

    /** Changements de compteur d'un pistolet (identifiant technique) sur [debut, fin), dans l'ordre chronologique. */
    List<ChangementCompteur> changementsCompteur(long pistoletId, Instant debut, Instant fin);

    /** Dernier changement de compteur d'un pistolet strictement avant un instant. */
    Optional<ChangementCompteur> dernierChangementAvant(long pistoletId, Instant avant);

    /** Pistolets d'une station (actifs ou non), par pompe puis par code. */
    List<PistoletRef> pistolets(long stationId);
}
