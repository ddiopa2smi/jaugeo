package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionDepot extends JpaRepository<Region, Long> {

    Optional<Region> findByCode(String code);

    List<Region> findAllByOrderByNomAsc();
}
