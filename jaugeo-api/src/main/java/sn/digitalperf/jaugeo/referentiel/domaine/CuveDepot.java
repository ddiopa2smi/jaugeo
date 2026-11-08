package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuveDepot extends JpaRepository<Cuve, Long> {

    Optional<Cuve> findByUuid(UUID uuid);

    List<Cuve> findByActifTrue();

    List<Cuve> findByStationIdOrderByCodeAsc(Long stationId);

    boolean existsByStationIdAndCodeIgnoreCase(Long stationId, String code);
}
