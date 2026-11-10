package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrixAchatDepot extends JpaRepository<PrixAchat, Long> {

    Optional<PrixAchat> findByUuid(UUID uuid);

    List<PrixAchat> findByFournisseurIdOrderByProduitIdAscDebutDesc(Long fournisseurId);

    /** Prix convenu en vigueur a cette date (le plus recent). */
    Optional<PrixAchat> findFirstByFournisseurIdAndProduitIdAndDebutLessThanEqualOrderByDebutDesc(Long fournisseurId,
                                                                                                    Long produitId,
                                                                                                    LocalDate date);

    boolean existsByFournisseurIdAndProduitIdAndDebut(Long fournisseurId, Long produitId, LocalDate debut);
}
