package sn.digitalperf.jaugeo.personnel.domaine;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeuilleCaisseDepot extends JpaRepository<FeuilleCaisse, Long> {

    List<FeuilleCaisse> findByJourneeIdOrderByIdAsc(Long journeeId);

    /** Feuilles figees (journee cloturee) d'une station sur une periode. */
    List<FeuilleCaisse> findByStationIdAndDateJourBetweenAndEcartIsNotNullOrderByDateJourAscIdAsc(Long stationId, LocalDate du,
                                                                                               LocalDate au);

    /** Feuilles figees d'un employe sur une periode, les plus recentes d'abord. */
    List<FeuilleCaisse> findByEmployeIdAndDateJourBetweenAndEcartIsNotNullOrderByDateJourDescIdDesc(Long employeId, LocalDate du,
                                                                                                LocalDate au);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FeuilleCaisse f where f.posteId = :posteId")
    int supprimerPourPoste(@Param("posteId") Long posteId);
}
