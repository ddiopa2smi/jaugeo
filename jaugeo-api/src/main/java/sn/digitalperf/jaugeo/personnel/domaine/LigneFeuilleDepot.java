package sn.digitalperf.jaugeo.personnel.domaine;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LigneFeuilleDepot extends JpaRepository<LigneFeuille, Long> {

    List<LigneFeuille> findByPosteIdInOrderByIdAsc(Collection<Long> posteIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LigneFeuille l where l.posteId = :posteId")
    int supprimerPourPoste(@Param("posteId") Long posteId);
}
