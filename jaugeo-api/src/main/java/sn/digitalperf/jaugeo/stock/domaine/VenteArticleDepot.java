package sn.digitalperf.jaugeo.stock.domaine;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenteArticleDepot extends JpaRepository<VenteArticle, Long> {

    List<VenteArticle> findByJourneeIdOrderByIdAsc(Long journeeId);

    /** Ventes saisies dans des journees pas encore cloturees (CMUP de sortie non fige) : stock reserve. */
    List<VenteArticle> findByStationIdAndCmupIsNull(Long stationId);

    /** Ventes des journees cloturees (CMUP fige) d'une station entre deux dates incluses. */
    List<VenteArticle> findByStationIdAndDateJourBetweenAndCmupIsNotNull(Long stationId, java.time.LocalDate du,
                                                                         java.time.LocalDate au);
}
