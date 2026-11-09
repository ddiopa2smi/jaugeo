package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormatReleveDepot extends JpaRepository<FormatReleve, Long> {

    Optional<FormatReleve> findByUuid(UUID uuid);

    Optional<FormatReleve> findByCode(String code);

    List<FormatReleve> findAllByOrderByCodeAsc();
}
