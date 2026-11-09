package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaiementManquantDepot extends JpaRepository<PaiementManquant, Long> {

    List<PaiementManquant> findByManquantIdInOrderByDatePaiementAscIdAsc(Collection<Long> manquantIds);
}
