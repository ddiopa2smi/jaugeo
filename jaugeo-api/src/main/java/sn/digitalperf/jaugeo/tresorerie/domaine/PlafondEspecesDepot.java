package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlafondEspecesDepot extends JpaRepository<PlafondEspeces, Long> {

    Optional<PlafondEspeces> findByStationId(Long stationId);
}
