package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointBaremeDepot extends JpaRepository<PointBareme, Long> {

    List<PointBareme> findByCuveIdOrderByHauteurMmAsc(Long cuveId);

    /** Suppression immediate (avant les insertions du nouveau bareme, contrainte d'unicite oblige). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from PointBareme p where p.cuveId = :cuveId")
    int supprimerPourCuve(@Param("cuveId") Long cuveId);

    @Query("select p.cuveId as cuveId, count(p) as nombre from PointBareme p where p.cuveId in :ids group by p.cuveId")
    List<NombreParCuve> compterParCuve(@Param("ids") Collection<Long> ids);

    interface NombreParCuve {
        Long getCuveId();

        long getNombre();
    }
}
