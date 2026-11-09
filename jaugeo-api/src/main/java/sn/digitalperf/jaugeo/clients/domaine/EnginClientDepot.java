package sn.digitalperf.jaugeo.clients.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnginClientDepot extends JpaRepository<EnginClient, Long> {

    Optional<EnginClient> findByUuid(UUID uuid);

    List<EnginClient> findByClientIdOrderByImmatriculationAsc(Long clientId);

    Optional<EnginClient> findByClientIdAndImmatriculationIgnoreCase(Long clientId, String immatriculation);

    boolean existsByClientIdAndActifTrue(Long clientId);
}
