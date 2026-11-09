package sn.digitalperf.jaugeo.clients.domaine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MouvementCarteDepot extends JpaRepository<MouvementCarte, Long> {

    Optional<MouvementCarte> findByUuid(UUID uuid);

    List<MouvementCarte> findByCarteIdOrderByDateMouvementDescIdDesc(Long carteId);

    List<MouvementCarte> findByJourneeIdOrderByIdAsc(Long journeeId);

    /** Valeur a la pompe des pleins payes par carte dans une journee. */
    @Query("select coalesce(sum(m.montantPompe), 0) from MouvementCarte m where m.journeeId = :journeeId")
    BigDecimal totalPompeJournee(@Param("journeeId") Long journeeId);

    /** [produitId, litres] des pleins payes par carte dans une journee. */
    @Query("select m.produitId, sum(m.litres) from MouvementCarte m where m.journeeId = :journeeId group by m.produitId")
    List<Object[]> litresParProduit(@Param("journeeId") Long journeeId);
}
