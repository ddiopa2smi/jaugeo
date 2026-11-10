package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersementDepot extends JpaRepository<Versement, Long> {

    Optional<Versement> findByUuid(UUID uuid);

    /** Versement non rejete portant deja cette reference dans cette banque (casse ignoree). */
    @Query("""
            select v from Versement v
            where upper(v.banque) = upper(:banque) and upper(v.referenceBv) = upper(:reference)
              and v.statut <> sn.digitalperf.jaugeo.tresorerie.domaine.Versement.Statut.REJETE""")
    Optional<Versement> parBordereau(@Param("banque") String banque, @Param("reference") String reference);

    List<Versement> findByStationIdAndDateDepotBetweenOrderByDateDepotAscIdAsc(Long stationId, LocalDate du, LocalDate au);

    long countByStationIdAndStatutAndDateDepotBetween(Long stationId, Versement.Statut statut, LocalDate du, LocalDate au);

    List<Versement> findByStationIdInAndStatutOrderByDateDepotAscIdAsc(Collection<Long> stationIds, Versement.Statut statut);

    /** Versements non rejetes deposes apres une date (exclue) jusqu'a une date incluse. */
    @Query("""
            select coalesce(sum(v.montant), 0) from Versement v
            where v.stationId = :stationId and v.dateDepot > :apres and v.dateDepot <= :au
              and v.statut <> sn.digitalperf.jaugeo.tresorerie.domaine.Versement.Statut.REJETE""")
    BigDecimal cumul(@Param("stationId") Long stationId, @Param("apres") LocalDate apres, @Param("au") LocalDate au);

    @Query("""
            select max(v.dateDepot) from Versement v
            where v.stationId = :stationId and v.statut <> sn.digitalperf.jaugeo.tresorerie.domaine.Versement.Statut.REJETE""")
    LocalDate dernierDepot(@Param("stationId") Long stationId);

    /** Versements non rejetes ni deja lettres d'un montant donne, deposes dans l'intervalle (candidats au lettrage). */
    @Query("""
            select v from Versement v
            where v.montant = :montant and v.dateDepot between :du and :au
              and v.statut <> sn.digitalperf.jaugeo.tresorerie.domaine.Versement.Statut.REJETE
              and not exists (select o from OperationReleve o where o.versementId = v.id)
            order by v.dateDepot, v.id""")
    List<Versement> candidatsLettrage(@Param("montant") BigDecimal montant, @Param("du") LocalDate du,
                                      @Param("au") LocalDate au);

    /** Versements non rejetes deposes sur une periode, toutes stations. */
    @Query("""
            select v from Versement v
            where v.dateDepot between :du and :au
              and v.statut <> sn.digitalperf.jaugeo.tresorerie.domaine.Versement.Statut.REJETE
            order by v.dateDepot, v.id""")
    List<Versement> deposesEntre(@Param("du") LocalDate du, @Param("au") LocalDate au);
}
