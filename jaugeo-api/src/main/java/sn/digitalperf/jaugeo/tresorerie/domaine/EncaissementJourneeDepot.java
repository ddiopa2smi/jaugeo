package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncaissementJourneeDepot extends JpaRepository<EncaissementJournee, Long> {

    List<EncaissementJournee> findByJourneeId(Long journeeId);

    List<EncaissementJournee> findByDateJourBetween(LocalDate du, LocalDate au);

    List<EncaissementJournee> findByStationIdAndDateJourBetween(Long stationId, LocalDate du, LocalDate au);

    List<EncaissementJournee> findByStationIdAndModeAndDateJourBetween(Long stationId, EncaissementJournee.Mode mode,
                                                                       LocalDate du, LocalDate au);
}
