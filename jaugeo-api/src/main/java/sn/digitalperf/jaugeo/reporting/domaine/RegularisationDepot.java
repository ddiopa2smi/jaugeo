package sn.digitalperf.jaugeo.reporting.domaine;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularisationDepot extends JpaRepository<Regularisation, Long> {

    /** Regularisations comptees dans le resultat d'un mois, pour une station. */
    List<Regularisation> findByStationIdAndMoisComptableOrderByIdAsc(Long stationId, LocalDate moisComptable);

    /** Regularisations des stations donnees comptees dans une periode de mois comptables, les plus recentes d'abord. */
    List<Regularisation> findByStationIdInAndMoisComptableBetweenOrderByIdDesc(Collection<Long> stationIds, LocalDate du,
                                                                               LocalDate au);
}
