package sn.digitalperf.jaugeo.depenses.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JustificatifDepot extends JpaRepository<Justificatif, Long> {

    Optional<Justificatif> findByDepenseId(Long depenseId);

    @Query("""
            select j.depenseId as depenseId, j.nomFichier as nomFichier, j.typeMime as typeMime, j.taille as taille
            from Justificatif j where j.depenseId in :depenseIds""")
    List<Justificatif.Description> descriptions(@Param("depenseIds") Collection<Long> depenseIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from Justificatif j where j.depenseId = :depenseId")
    int supprimerPourDepense(@Param("depenseId") Long depenseId);
}
