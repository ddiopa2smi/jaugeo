package sn.digitalperf.jaugeo.approvisionnement.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LigneFactureFournisseurDepot extends JpaRepository<LigneFactureFournisseur, Long> {

    List<LigneFactureFournisseur> findByFactureIdOrderByOrdreAsc(Long factureId);

    List<LigneFactureFournisseur> findByFactureIdIn(Collection<Long> factureIds);

    /** Lignes d'autres factures non annulees du meme fournisseur portant ce BL. */
    @Query("""
            select l from LigneFactureFournisseur l, FactureFournisseur f
            where f.id = l.factureId and f.fournisseurId = :fournisseurId and f.id <> :factureId
              and upper(l.numeroBl) = upper(:numeroBl) and f.statut <> :annulee""")
    List<LigneFactureFournisseur> dejaFacture(@Param("fournisseurId") Long fournisseurId, @Param("factureId") Long factureId,
                                              @Param("numeroBl") String numeroBl,
                                              @Param("annulee") FactureFournisseur.Statut annulee);

    @Modifying(flushAutomatically = true)
    @Query("delete from LigneFactureFournisseur l where l.factureId = :factureId")
    int supprimerPourFacture(@Param("factureId") Long factureId);
}
