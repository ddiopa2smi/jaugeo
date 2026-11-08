package sn.digitalperf.jaugeo.securite.domaine;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JetonDepot extends JpaRepository<JetonRafraichissement, Long> {

    Optional<JetonRafraichissement> findByEmpreinte(String empreinte);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoqueLe = :maintenant where j.utilisateur.id = :utilisateurId and j.revoqueLe is null")
    int revoquerTous(@Param("utilisateurId") Long utilisateurId, @Param("maintenant") Instant maintenant);

    @Transactional
    @Modifying
    @Query("delete from JetonRafraichissement j where j.expireLe < :limite")
    int purgerExpires(@Param("limite") Instant limite);
}
