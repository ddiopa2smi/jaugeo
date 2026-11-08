package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationDepot extends JpaRepository<Station, Long> {

    Optional<Station> findByUuid(UUID uuid);

    boolean existsByCodeIgnoreCase(String code);

    List<Station> findAllByOrderByCodeAsc();

    List<Station> findByIdInOrderByCodeAsc(Collection<Long> ids);
}
