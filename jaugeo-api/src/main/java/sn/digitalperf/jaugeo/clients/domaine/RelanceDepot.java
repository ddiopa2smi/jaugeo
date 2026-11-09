package sn.digitalperf.jaugeo.clients.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelanceDepot extends JpaRepository<Relance, Long> {

    Optional<Relance> findByUuid(UUID uuid);

    List<Relance> findByClientIdOrderByDateRelanceDescIdDesc(Long clientId);

    List<Relance> findByClientIdIn(Collection<Long> clientIds);
}
