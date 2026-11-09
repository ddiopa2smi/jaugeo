package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationReleveDepot extends JpaRepository<OperationReleve, Long> {

    Optional<OperationReleve> findByUuid(UUID uuid);

    boolean existsByFormatIdAndCle(Long formatId, String cle);

    List<OperationReleve> findByVersementIdIn(Collection<Long> versementIds);

    /** Operations d'une nature de releve sur une periode. */
    @Query("""
            select o from OperationReleve o, FormatReleve f
            where f.id = o.formatId and f.nature = :nature and o.dateOperation between :du and :au
            order by o.dateOperation, o.id""")
    List<OperationReleve> parNature(@Param("nature") FormatReleve.Nature nature, @Param("du") LocalDate du,
                                    @Param("au") LocalDate au);

    /** Credits bancaires pas encore lettres, quelle que soit leur date. */
    @Query("""
            select o from OperationReleve o, FormatReleve f
            where f.id = o.formatId and f.nature = sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleve.Nature.BANQUE
              and o.versementId is null
            order by o.dateOperation, o.id""")
    List<OperationReleve> bancairesNonLettrees();
}
