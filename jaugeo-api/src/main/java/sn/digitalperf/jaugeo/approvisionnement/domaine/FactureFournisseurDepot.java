package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactureFournisseurDepot extends JpaRepository<FactureFournisseur, Long> {

    Optional<FactureFournisseur> findByUuid(UUID uuid);

    List<FactureFournisseur> findAllByOrderByDateFactureDescIdDesc();

    Optional<FactureFournisseur> findByFournisseurIdAndNumeroIgnoreCase(Long fournisseurId, String numero);
}
