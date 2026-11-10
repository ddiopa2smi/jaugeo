package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiterneDepot extends JpaRepository<Citerne, Long> {

    Optional<Citerne> findByUuid(UUID uuid);

    List<Citerne> findByTransporteurIdOrderByImmatriculationAsc(Long transporteurId);

    Optional<Citerne> findByImmatriculationIgnoreCase(String immatriculation);
}
