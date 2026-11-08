package sn.digitalperf.jaugeo.referentiel.domaine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvenementEquipementDepot extends JpaRepository<EvenementEquipement, Long> {

    @Query("""
            select e from EvenementEquipement e
            left join fetch e.pistolet pi left join fetch pi.pompe
            left join fetch e.pompe left join fetch e.cuve
            where e.station.id = :stationId
            order by e.date desc, e.id desc""")
    List<EvenementEquipement> historiqueStation(@Param("stationId") Long stationId);

    @Query("""
            select e from EvenementEquipement e
            where e.pistolet.id = :pistoletId
              and e.type in (sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement.Type.REMPLACEMENT_TOTALISATEUR,
                             sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement.Type.REMISE_A_ZERO)
            order by e.date desc, e.id desc
            limit 1""")
    Optional<EvenementEquipement> dernierChangementCompteur(@Param("pistoletId") Long pistoletId);

    @Query("""
            select e from EvenementEquipement e
            where e.pistolet.id = :pistoletId
              and e.type in (sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement.Type.REMPLACEMENT_TOTALISATEUR,
                             sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement.Type.REMISE_A_ZERO)
              and e.date >= :debut and e.date < :fin
            order by e.date asc, e.id asc""")
    List<EvenementEquipement> changementsCompteur(@Param("pistoletId") Long pistoletId,
                                                  @Param("debut") Instant debut, @Param("fin") Instant fin);
}
