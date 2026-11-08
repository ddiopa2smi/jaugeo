package sn.digitalperf.jaugeo.clients.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDepot extends JpaRepository<Client, Long> {

    Optional<Client> findByUuid(UUID uuid);

    boolean existsByCodeIgnoreCase(String code);

    List<Client> findAllByOrderByCodeAsc();
}
