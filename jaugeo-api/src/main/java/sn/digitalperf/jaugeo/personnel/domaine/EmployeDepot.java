package sn.digitalperf.jaugeo.personnel.domaine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeDepot extends JpaRepository<Employe, Long> {

    Optional<Employe> findByUuid(UUID uuid);

    List<Employe> findByStationIdOrderByNomAscPrenomAsc(Long stationId);

    Optional<Employe> findByMatriculeIgnoreCase(String matricule);

    Optional<Employe> findByIdentifiantIgnoreCase(String identifiant);
}
