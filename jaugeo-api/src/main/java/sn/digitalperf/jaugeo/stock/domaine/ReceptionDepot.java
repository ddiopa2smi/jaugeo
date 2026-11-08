package sn.digitalperf.jaugeo.stock.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceptionDepot extends JpaRepository<Reception, Long> {

    Optional<Reception> findByUuid(UUID uuid);

    List<Reception> findByJourneeIdOrderByIdAsc(Long journeeId);

    /** Reception deja enregistree pour ce BL de ce fournisseur (casse ignoree). */
    @Query("""
            select r from Reception r
            where upper(r.fournisseur) = upper(:fournisseur) and upper(r.numeroBl) = upper(:numeroBl)""")
    Optional<Reception> parBl(@Param("fournisseur") String fournisseur, @Param("numeroBl") String numeroBl);
}
