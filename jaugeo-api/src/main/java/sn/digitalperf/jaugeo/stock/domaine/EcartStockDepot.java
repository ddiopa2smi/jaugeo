package sn.digitalperf.jaugeo.stock.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EcartStockDepot extends JpaRepository<EcartStock, Long> {

    interface Cumul {
        BigDecimal getSorties();

        BigDecimal getEcart();
    }

    List<EcartStock> findByJourneeId(Long journeeId);

    /** Cumul des sorties et des ecarts d'une cuve sur [du, au). */
    @Query("""
            select coalesce(sum(e.sorties), 0) as sorties, coalesce(sum(e.ecartLitres), 0) as ecart
            from EcartStock e where e.cuveId = :cuveId and e.dateJour >= :du and e.dateJour < :au""")
    Cumul cumul(@Param("cuveId") Long cuveId, @Param("du") LocalDate du, @Param("au") LocalDate au);

    List<EcartStock> findByStationIdAndDateJourBetweenOrderByDateJourAscCuveIdAsc(Long stationId, LocalDate du, LocalDate au);

    /** Derniere ligne figee de chaque cuve de ces stations. */
    @Query("""
            select e from EcartStock e
            where e.stationId in :stationIds
              and e.dateJour = (select max(x.dateJour) from EcartStock x where x.cuveId = e.cuveId)
            order by e.stationId, e.cuveId""")
    List<EcartStock> dernieres(@Param("stationIds") Collection<Long> stationIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from EcartStock e where e.journeeId = :journeeId")
    int supprimerPourJournee(@Param("journeeId") Long journeeId);
}
