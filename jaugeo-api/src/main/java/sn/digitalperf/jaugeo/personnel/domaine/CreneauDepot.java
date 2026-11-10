package sn.digitalperf.jaugeo.personnel.domaine;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreneauDepot extends JpaRepository<Creneau, Long> {

    List<Creneau> findByStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(Long stationId, LocalDate du, LocalDate au);

    List<Creneau> findByStationIdAndDateJour(Long stationId, LocalDate dateJour);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Creneau c where c.stationId = :stationId and c.dateJour between :du and :au")
    int supprimer(@Param("stationId") Long stationId, @Param("du") LocalDate du, @Param("au") LocalDate au);
}
