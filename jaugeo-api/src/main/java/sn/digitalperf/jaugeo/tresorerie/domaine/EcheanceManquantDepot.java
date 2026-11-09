package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EcheanceManquantDepot extends JpaRepository<EcheanceManquant, Long> {

    List<EcheanceManquant> findByManquantIdInOrderByManquantIdAscRangAsc(Collection<Long> manquantIds);

    List<EcheanceManquant> findByManquantIdOrderByRangAsc(Long manquantId);
}
