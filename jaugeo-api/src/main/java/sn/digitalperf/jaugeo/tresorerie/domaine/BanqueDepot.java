package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BanqueDepot extends JpaRepository<Banque, Long> {

    Optional<Banque> findByUuid(UUID uuid);

    Optional<Banque> findByCode(String code);

    List<Banque> findAllByOrderByNomAsc();

    List<Banque> findByActifTrueOrderByNomAsc();
}
