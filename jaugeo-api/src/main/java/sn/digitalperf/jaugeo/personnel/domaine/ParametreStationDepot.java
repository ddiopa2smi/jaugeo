package sn.digitalperf.jaugeo.personnel.domaine;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametreStationDepot extends JpaRepository<ParametreStation, Long> {

    Optional<ParametreStation> findByStationId(Long stationId);
}
