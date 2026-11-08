package sn.digitalperf.jaugeo.securite.domaine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UtilisateurDepot extends JpaRepository<Utilisateur, Long> {

    @Query("select u from Utilisateur u where lower(u.identifiant) = lower(:identifiant)")
    Optional<Utilisateur> trouverParIdentifiant(@Param("identifiant") String identifiant);

    Optional<Utilisateur> findByUuid(UUID uuid);

    List<Utilisateur> findAllByOrderByNomCompletAsc();

    // Suivi de connexion : mise a jour directe, sans toucher modifie_par (pas un evenement d'audit metier)
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Utilisateur u set u.echecsConnexion = :echecs, u.verrouilleJusqua = :verrou where u.id = :id")
    void enregistrerEchec(@Param("id") Long id, @Param("echecs") int echecs, @Param("verrou") Instant verrou);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Utilisateur u set u.echecsConnexion = 0, u.verrouilleJusqua = null, u.derniereConnexion = :maintenant where u.id = :id")
    void enregistrerSucces(@Param("id") Long id, @Param("maintenant") Instant maintenant);
}
