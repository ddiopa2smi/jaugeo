package sn.digitalperf.jaugeo.depenses.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NatureDepenseDepot extends JpaRepository<NatureDepense, Long> {

    Optional<NatureDepense> findByUuid(UUID uuid);

    boolean existsByCodeIgnoreCase(String code);

    List<NatureDepense> findAllByOrderByLibelleAsc();
}
