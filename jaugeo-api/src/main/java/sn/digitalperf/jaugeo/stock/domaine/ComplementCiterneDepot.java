package sn.digitalperf.jaugeo.stock.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplementCiterneDepot extends JpaRepository<ComplementCiterne, Long> {

    Optional<ComplementCiterne> findByUuid(UUID uuid);

    List<ComplementCiterne> findByJourneeId(Long journeeId);

    List<ComplementCiterne> findByReceptionIdIn(Collection<Long> receptionIds);

    List<ComplementCiterne> findByStationIdInOrderByDateJourDesc(Collection<Long> stationIds);

    List<ComplementCiterne> findAllByOrderByDateJourDesc();

    @Modifying(flushAutomatically = true)
    @Query("delete from ComplementCiterne c where c.journeeId = :journeeId")
    int supprimerPourJournee(@Param("journeeId") Long journeeId);
}
