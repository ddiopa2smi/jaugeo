package sn.digitalperf.jaugeo.reporting.domaine;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompteFigeDepot extends JpaRepository<CompteFige, Long> {

    List<CompteFige> findByClotureIdOrderByIdAsc(Long clotureId);
}
