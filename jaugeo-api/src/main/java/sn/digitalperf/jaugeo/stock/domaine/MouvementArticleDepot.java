package sn.digitalperf.jaugeo.stock.domaine;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MouvementArticleDepot extends JpaRepository<MouvementArticle, Long> {

    List<MouvementArticle> findByStationIdAndDateJourBetweenOrderByIdDesc(Long stationId, LocalDate du, LocalDate au);

    List<MouvementArticle> findByJourneeIdAndType(Long journeeId, MouvementArticle.Type type);

    @Modifying(flushAutomatically = true)
    @Query("delete from MouvementArticle m where m.journeeId = :journeeId and m.type = :type")
    int supprimer(@Param("journeeId") Long journeeId, @Param("type") MouvementArticle.Type type);
}
