package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManquantGerantDepot extends JpaRepository<ManquantGerant, Long> {

    Optional<ManquantGerant> findByUuid(UUID uuid);

    Optional<ManquantGerant> findByArreteId(Long arreteId);

    List<ManquantGerant> findByStationIdInOrderByDateJourDesc(Collection<Long> stationIds);
}
