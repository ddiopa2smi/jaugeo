package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FournisseurDepot extends JpaRepository<Fournisseur, Long> {

    Optional<Fournisseur> findByUuid(UUID uuid);

    List<Fournisseur> findAllByOrderByNomAsc();

    Optional<Fournisseur> findByPrincipalTrue();

    Optional<Fournisseur> findByNomIgnoreCase(String nom);
}
