package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PieceVersementDepot extends JpaRepository<PieceVersement, Long> {

    Optional<PieceVersement> findByVersementId(Long versementId);

    @Query("""
            select p.versementId as versementId, p.nomFichier as nomFichier, p.typeMime as typeMime, p.taille as taille
            from PieceVersement p where p.versementId in :versementIds""")
    List<PieceVersement.Description> descriptions(@Param("versementIds") Collection<Long> versementIds);

    @Query("select count(p) > 0 from PieceVersement p where p.versementId = :versementId")
    boolean existe(@Param("versementId") Long versementId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PieceVersement p where p.versementId = :versementId")
    int supprimerPourVersement(@Param("versementId") Long versementId);
}
