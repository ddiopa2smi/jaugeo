package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TourneeDepot extends JpaRepository<Tournee, Long> {

    Optional<Tournee> findByUuid(UUID uuid);

    List<Tournee> findAllByOrderByDateLivraisonPrevueDescIdDesc();

    List<Tournee> findByStatutIn(Collection<Tournee.Statut> statuts);

    List<Tournee> findByTransporteurId(Long transporteurId);

    @Query(value = "select nextval('approvisionnement.numero_tournee')", nativeQuery = true)
    long prochainNumero();
}
