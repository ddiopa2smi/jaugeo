package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaliteDepot extends JpaRepository<Penalite, Long> {

    Optional<Penalite> findByUuid(UUID uuid);

    List<Penalite> findByJourneeId(Long journeeId);

    List<Penalite> findAllByOrderByDateJourDescIdDesc();

    List<Penalite> findByTransporteurIdOrderByDateJourDesc(Long transporteurId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Penalite p where p.journeeId = :journeeId")
    int supprimerPourJournee(@Param("journeeId") Long journeeId);
}
