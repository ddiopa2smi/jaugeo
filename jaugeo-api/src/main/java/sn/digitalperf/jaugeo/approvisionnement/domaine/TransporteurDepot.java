package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransporteurDepot extends JpaRepository<Transporteur, Long> {

    Optional<Transporteur> findByUuid(UUID uuid);

    List<Transporteur> findAllByOrderByNomAsc();

    Optional<Transporteur> findByNomIgnoreCase(String nom);
}
