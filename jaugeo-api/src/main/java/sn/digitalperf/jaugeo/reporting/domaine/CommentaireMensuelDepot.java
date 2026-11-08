package sn.digitalperf.jaugeo.reporting.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentaireMensuelDepot extends JpaRepository<CommentaireMensuel, Long> {

    Optional<CommentaireMensuel> findByStationIdAndMois(Long stationId, LocalDate mois);

    List<CommentaireMensuel> findByMois(LocalDate mois);
}
