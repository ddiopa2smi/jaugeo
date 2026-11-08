package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecetteJourneeDepot extends JpaRepository<RecetteJournee, Long> {

    Optional<RecetteJournee> findByJourneeId(Long journeeId);

    List<RecetteJournee> findByStationIdAndDateJourBetweenOrderByDateJourAsc(Long stationId, LocalDate du, LocalDate au);

    /** Dernieres recettes jusqu'a une date incluse, de la plus recente a la plus ancienne. */
    List<RecetteJournee> findByStationIdAndDateJourLessThanEqualOrderByDateJourDesc(Long stationId, LocalDate au, Limit limite);

    /** Somme des montants a verser apres une date (exclue) jusqu'a une date incluse. */
    @Query("""
            select coalesce(sum(r.montantAVerser), 0) from RecetteJournee r
            where r.stationId = :stationId and r.dateJour > :apres and r.dateJour <= :au""")
    BigDecimal cumul(@Param("stationId") Long stationId, @Param("apres") LocalDate apres, @Param("au") LocalDate au);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecetteJournee r where r.journeeId = :journeeId")
    int supprimerPourJournee(@Param("journeeId") Long journeeId);
}
