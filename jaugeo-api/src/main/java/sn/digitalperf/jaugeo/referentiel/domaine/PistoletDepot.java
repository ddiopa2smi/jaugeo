package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PistoletDepot extends JpaRepository<Pistolet, Long> {

    @Query("select p from Pistolet p join fetch p.pompe po where po.station.id = :stationId order by po.code, p.code")
    List<Pistolet> parStation(@Param("stationId") Long stationId);

    Optional<Pistolet> findByUuid(UUID uuid);

    boolean existsByPompeIdAndCodeIgnoreCase(Long pompeId, String code);
}
