package sn.digitalperf.jaugeo.personnel.domaine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffectationDepot extends JpaRepository<Affectation, Long> {

    List<Affectation> findByEmployeIdOrderByDebutDescIdDesc(Long employeId);

    Optional<Affectation> findFirstByEmployeIdAndFinIsNullOrderByDebutDescIdDesc(Long employeId);
}
