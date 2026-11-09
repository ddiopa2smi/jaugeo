package sn.digitalperf.jaugeo.clients.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemiseClientDepot extends JpaRepository<RemiseClient, Long> {

    Optional<RemiseClient> findByUuid(UUID uuid);

    List<RemiseClient> findByClientIdOrderByProduitIdAscDebutDesc(Long clientId);

    /** Remise en vigueur a une date : la plus recente commencee ce jour-la ou avant. */
    Optional<RemiseClient> findFirstByClientIdAndProduitIdAndDebutLessThanEqualOrderByDebutDesc(Long clientId, Long produitId,
                                                                                                  LocalDate date);

    boolean existsByClientIdAndProduitIdAndDebut(Long clientId, Long produitId, LocalDate debut);
}
