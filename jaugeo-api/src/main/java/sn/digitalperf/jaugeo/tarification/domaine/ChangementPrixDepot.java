package sn.digitalperf.jaugeo.tarification.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangementPrixDepot extends JpaRepository<ChangementPrix, Long> {

    Optional<ChangementPrix> findByUuid(UUID uuid);

    List<ChangementPrix> findAllByOrderByDebutDescIdDesc();
}
