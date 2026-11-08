package sn.digitalperf.jaugeo.depenses.domaine;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepenseDepot extends JpaRepository<Depense, Long> {

    Optional<Depense> findByUuid(UUID uuid);

    List<Depense> findByJourneeIdOrderByIdAsc(Long journeeId);

    List<Depense> findByStationIdInAndStatutOrderByDateJourAscIdAsc(Collection<Long> stationIds, Depense.Statut statut);

    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
            where d.journeeId = :journeeId and d.statut = sn.digitalperf.jaugeo.depenses.domaine.Depense.Statut.AUTORISEE""")
    BigDecimal totalAutorise(@Param("journeeId") Long journeeId);
}
