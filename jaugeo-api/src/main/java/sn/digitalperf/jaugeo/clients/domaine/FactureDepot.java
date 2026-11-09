package sn.digitalperf.jaugeo.clients.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FactureDepot extends JpaRepository<Facture, Long> {

    Optional<Facture> findByUuid(UUID uuid);

    List<Facture> findByClientIdOrderByDateFactureDescIdDesc(Long clientId);

    List<Facture> findByClientIdAndStatut(Long clientId, Facture.Statut statut);

    List<Facture> findByClientIdInAndStatut(Collection<Long> clientIds, Facture.Statut statut);

    @Query(value = "select nextval('clients.numero_facture')", nativeQuery = true)
    long prochainNumero();
}
