package sn.digitalperf.jaugeo.reporting.domaine;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetVenteDepot extends JpaRepository<BudgetVente, Long> {

    List<BudgetVente> findByStationIdInAndMoisBetween(Collection<Long> stationIds, LocalDate du, LocalDate au);
}
