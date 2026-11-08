package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PompeDepot extends JpaRepository<Pompe, Long> {

    Optional<Pompe> findByUuid(UUID uuid);

    List<Pompe> findByStationIdOrderByCodeAsc(Long stationId);

    boolean existsByStationIdAndCodeIgnoreCase(Long stationId, String code);
}
