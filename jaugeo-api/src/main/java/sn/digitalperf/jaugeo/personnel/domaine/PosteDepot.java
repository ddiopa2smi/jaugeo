package sn.digitalperf.jaugeo.personnel.domaine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosteDepot extends JpaRepository<Poste, Long> {

    List<Poste> findByJourneeId(Long journeeId);

    Optional<Poste> findByJourneeIdAndType(Long journeeId, TypePoste type);
}
