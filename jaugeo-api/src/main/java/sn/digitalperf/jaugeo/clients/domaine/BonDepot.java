package sn.digitalperf.jaugeo.clients.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BonDepot extends JpaRepository<Bon, Long> {

    Optional<Bon> findByUuid(UUID uuid);

    List<Bon> findByJourneeIdOrderByIdAsc(Long journeeId);

    Optional<Bon> findByStationIdAndNumeroIgnoreCase(Long stationId, String numero);

    List<Bon> findByClientIdAndStationIdAndDateJourBetweenOrderByDateJourAscIdAsc(Long clientId, Long stationId,
                                                                                    LocalDate du, LocalDate au);

    /** [recouvrement, montant] des bons d'une journee. */
    @Query("select b.recouvrement, coalesce(sum(b.montant), 0) from Bon b where b.journeeId = :journeeId group by b.recouvrement")
    List<Object[]> totauxJournee(@Param("journeeId") Long journeeId);

    /** [produitId, litres] des bons d'une journee. */
    @Query("select b.produitId, sum(b.litres) from Bon b where b.journeeId = :journeeId group by b.produitId")
    List<Object[]> litresParProduit(@Param("journeeId") Long journeeId);

    /** Total des bons d'un client, toutes stations, hors un bon (en cours de correction). */
    @Query("select coalesce(sum(b.montant), 0) from Bon b where b.clientId = :clientId and b.id <> :exclu")
    BigDecimal totalClient(@Param("clientId") Long clientId, @Param("exclu") Long exclu);

    /** [clientId, montant] des bons d'une station entre deux dates incluses. */
    @Query("""
            select b.clientId, coalesce(sum(b.montant), 0) from Bon b
            where b.stationId = :stationId and b.dateJour between :du and :au group by b.clientId""")
    List<Object[]> totauxParClient(@Param("stationId") Long stationId, @Param("du") LocalDate du, @Param("au") LocalDate au);

    /** [stationId, montant] des bons d'un client. */
    @Query("select b.stationId, coalesce(sum(b.montant), 0) from Bon b where b.clientId = :clientId group by b.stationId")
    List<Object[]> totauxParStation(@Param("clientId") Long clientId);

    /** [clientId, montant] des bons de tout le reseau. */
    @Query("select b.clientId, coalesce(sum(b.montant), 0) from Bon b group by b.clientId")
    List<Object[]> totauxReseau();

    @Query("select max(b.dateJour) from Bon b where b.clientId = :clientId and b.stationId = :stationId")
    LocalDate dernierBon(@Param("clientId") Long clientId, @Param("stationId") Long stationId);
}
