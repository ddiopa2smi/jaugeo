package sn.digitalperf.jaugeo.stock.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntreeArticleDepot extends JpaRepository<EntreeArticle, Long> {

    Optional<EntreeArticle> findByUuid(UUID uuid);

    List<EntreeArticle> findByStationIdAndStatutOrderByIdAsc(Long stationId, EntreeArticle.Statut statut);
}
