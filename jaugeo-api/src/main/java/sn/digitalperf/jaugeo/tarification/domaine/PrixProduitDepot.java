package sn.digitalperf.jaugeo.tarification.domaine;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrixProduitDepot extends JpaRepository<PrixProduit, Long> {

    /** Dernier prix (le plus recent par date d'effet), verrouille pour le remplacer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PrixProduit p
            where p.stationId = :stationId and p.produitId = :produitId
            order by p.debut desc limit 1""")
    Optional<PrixProduit> dernierPourMiseAJour(@Param("stationId") Long stationId, @Param("produitId") Long produitId);

    @Query("""
            select p from PrixProduit p
            where p.stationId = :stationId and p.produitId = :produitId
              and p.debut <= :instant and (p.fin is null or p.fin > :instant)""")
    Optional<PrixProduit> enVigueur(@Param("stationId") Long stationId, @Param("produitId") Long produitId,
                                    @Param("instant") Instant instant);

    /** Prix dont la periode chevauche [debut, fin), dans l'ordre chronologique. */
    @Query("""
            select p from PrixProduit p
            where p.stationId = :stationId and p.produitId = :produitId
              and p.debut < :fin and (p.fin is null or p.fin > :debut)
            order by p.debut asc""")
    List<PrixProduit> chevauchant(@Param("stationId") Long stationId, @Param("produitId") Long produitId,
                                  @Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Prix en vigueur ou a venir (fin nulle ou future) des stations donnees. */
    @Query("""
            select p from PrixProduit p
            where p.stationId in :stationIds and (p.fin is null or p.fin > :instant)
            order by p.stationId, p.produitId, p.debut""")
    List<PrixProduit> courantsEtFuturs(@Param("stationIds") Collection<Long> stationIds, @Param("instant") Instant instant);

    @Query("select p from PrixProduit p join fetch p.changement where p.stationId = :stationId order by p.debut desc, p.produitId")
    List<PrixProduit> historiqueStation(@Param("stationId") Long stationId);

    List<PrixProduit> findByChangementId(Long changementId);

    @Query("""
            select p from PrixProduit p
            where p.stationId = :stationId and p.produitId = :produitId and p.fin = :fin""")
    Optional<PrixProduit> precedent(@Param("stationId") Long stationId, @Param("produitId") Long produitId,
                                    @Param("fin") Instant fin);

    @Query("select p from PrixProduit p where p.changement.id in :ids")
    List<PrixProduit> parChangements(@Param("ids") Collection<Long> ids);
}
