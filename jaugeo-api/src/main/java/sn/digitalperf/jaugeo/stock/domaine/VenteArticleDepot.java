package sn.digitalperf.jaugeo.stock.domaine;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenteArticleDepot extends JpaRepository<VenteArticle, Long> {

    List<VenteArticle> findByJourneeIdOrderByIdAsc(Long journeeId);

    /** Ventes saisies dans des journees pas encore cloturees (CMUP de sortie non fige) : stock reserve. */
    List<VenteArticle> findByStationIdAndCmupIsNull(Long stationId);
}
