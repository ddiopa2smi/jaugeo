package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArreteCaisseDepot extends JpaRepository<ArreteCaisse, Long> {

    /** Dernier arrete a cette date ou avant. */
    Optional<ArreteCaisse> findFirstByStationIdAndDateJourLessThanEqualOrderByDateJourDesc(Long stationId, LocalDate date);

    Optional<ArreteCaisse> findFirstByStationIdOrderByDateJourDesc(Long stationId);

    boolean existsByStationIdAndDateJourGreaterThanEqual(Long stationId, LocalDate date);

    List<ArreteCaisse> findByStationIdAndDateJourBetweenOrderByDateJourAsc(Long stationId, LocalDate du, LocalDate au);

    List<ArreteCaisse> findByStationIdOrderByDateJourDesc(Long stationId);
}
