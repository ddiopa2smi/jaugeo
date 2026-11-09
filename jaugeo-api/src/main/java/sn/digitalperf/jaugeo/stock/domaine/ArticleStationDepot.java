package sn.digitalperf.jaugeo.stock.domaine;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleStationDepot extends JpaRepository<ArticleStation, Long> {

    List<ArticleStation> findByStationId(Long stationId);

    Optional<ArticleStation> findByStationIdAndProduitId(Long stationId, Long produitId);

    /** Stock d'un article verrouille pour un mouvement (entree, vente, comptage). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ArticleStation a where a.stationId = :stationId and a.produitId = :produitId")
    Optional<ArticleStation> verrouiller(@Param("stationId") Long stationId, @Param("produitId") Long produitId);
}
