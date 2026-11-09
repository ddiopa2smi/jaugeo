package sn.digitalperf.jaugeo.clients.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarteDepot extends JpaRepository<Carte, Long> {

    Optional<Carte> findByUuid(UUID uuid);

    Optional<Carte> findByNumero(String numero);

    boolean existsByNumero(String numero);

    List<Carte> findByClientIdOrderByNumeroAsc(Long clientId);
}
