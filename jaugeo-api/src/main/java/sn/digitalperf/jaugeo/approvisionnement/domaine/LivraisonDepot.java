package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LivraisonDepot extends JpaRepository<Livraison, Long> {

    Optional<Livraison> findByUuid(UUID uuid);

    List<Livraison> findByTourneeIdOrderByOrdreAsc(Long tourneeId);

    List<Livraison> findByTourneeIdIn(Collection<Long> tourneeIds);

    /** Livraisons des tournees non annulees vers une station. */
    @Query("""
            select l from Livraison l, Tournee t
            where t.id = l.tourneeId and l.stationId = :stationId and t.statut <> :annulee
            order by t.dateLivraisonPrevue, l.ordre""")
    List<Livraison> enCoursVers(@Param("stationId") Long stationId, @Param("annulee") Tournee.Statut annulee);

    /** Livraisons dont le BL porte ce numero, pour un depot (tournees non annulees). */
    @Query("""
            select l from Livraison l, Tournee t
            where t.id = l.tourneeId and t.fournisseurId = :fournisseurId and upper(l.numeroBl) = upper(:numeroBl)
              and t.statut <> :annulee""")
    List<Livraison> parBl(@Param("fournisseurId") Long fournisseurId, @Param("numeroBl") String numeroBl,
                          @Param("annulee") Tournee.Statut annulee);
}
