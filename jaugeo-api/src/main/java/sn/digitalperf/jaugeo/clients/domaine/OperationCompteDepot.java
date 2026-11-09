package sn.digitalperf.jaugeo.clients.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationCompteDepot extends JpaRepository<OperationCompte, Long> {

    Optional<OperationCompte> findByUuid(UUID uuid);

    List<OperationCompte> findByJourneeIdOrderByIdAsc(Long journeeId);

    List<OperationCompte> findByClientIdIn(java.util.Collection<Long> clientIds);

    List<OperationCompte> findByClientIdAndStationIdAndDateOperationBetweenOrderByDateOperationAscIdAsc(
            Long clientId, Long stationId, LocalDate du, LocalDate au);

    @Query("select coalesce(sum(o.montant), 0) from OperationCompte o where o.journeeId = :journeeId")
    BigDecimal totalJournee(@Param("journeeId") Long journeeId);

    /** [clientId, type, mode, montant] des operations d'une station entre deux dates incluses. */
    @Query("""
            select o.clientId, o.type, o.mode, coalesce(sum(o.montant), 0) from OperationCompte o
            where o.stationId = :stationId and o.dateOperation between :du and :au group by o.clientId, o.type, o.mode""")
    List<Object[]> totauxParClient(@Param("stationId") Long stationId, @Param("du") LocalDate du, @Param("au") LocalDate au);

    /** [stationId, type, montant] des operations d'un client. */
    @Query("""
            select o.stationId, o.type, coalesce(sum(o.montant), 0) from OperationCompte o
            where o.clientId = :clientId group by o.stationId, o.type""")
    List<Object[]> totauxParStation(@Param("clientId") Long clientId);

    /** [clientId, type, montant] des operations de tout le reseau. */
    @Query("select o.clientId, o.type, coalesce(sum(o.montant), 0) from OperationCompte o group by o.clientId, o.type")
    List<Object[]> totauxReseau();

    @Query("""
            select max(o.dateOperation) from OperationCompte o where o.clientId = :clientId and o.stationId = :stationId
            and o.type = sn.digitalperf.jaugeo.clients.domaine.OperationCompte.Type.REGLEMENT""")
    LocalDate dernierReglement(@Param("clientId") Long clientId, @Param("stationId") Long stationId);
}
