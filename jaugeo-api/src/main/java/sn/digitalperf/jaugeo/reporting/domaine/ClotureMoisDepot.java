package sn.digitalperf.jaugeo.reporting.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClotureMoisDepot extends JpaRepository<ClotureMois, Long> {

    boolean existsByStationIdAndMois(Long stationId, LocalDate mois);

    Optional<ClotureMois> findByStationIdAndMois(Long stationId, LocalDate mois);

    List<ClotureMois> findByMois(LocalDate mois);

    /** Mois clos d'une station, du plus recent au plus ancien. */
    List<ClotureMois> findByStationIdOrderByMoisDesc(Long stationId);
}
