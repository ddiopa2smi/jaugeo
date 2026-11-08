package sn.digitalperf.jaugeo.stock.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JaugeageDepot extends JpaRepository<Jaugeage, Long> {

    List<Jaugeage> findByJourneeId(Long journeeId);

    Optional<Jaugeage> findByJourneeIdAndCuveIdAndMoment(Long journeeId, Long cuveId, Jaugeage.Moment moment);

    /** Jaugeage de cloture d'une cuve a une date : stock physique dont part le lendemain. */
    Optional<Jaugeage> findByCuveIdAndDateJourAndMoment(Long cuveId, LocalDate dateJour, Jaugeage.Moment moment);
}
