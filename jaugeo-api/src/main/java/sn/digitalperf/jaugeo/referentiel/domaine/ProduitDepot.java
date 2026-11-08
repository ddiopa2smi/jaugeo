package sn.digitalperf.jaugeo.referentiel.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProduitDepot extends JpaRepository<Produit, Long> {

    Optional<Produit> findByUuid(UUID uuid);

    Optional<Produit> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Produit> findAllByOrderByFamilleAscLibelleAsc();
}
