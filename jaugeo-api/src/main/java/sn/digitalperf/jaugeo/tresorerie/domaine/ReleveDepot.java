package sn.digitalperf.jaugeo.tresorerie.domaine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReleveDepot extends JpaRepository<Releve, Long> {

    Optional<Releve> findByEmpreinte(String empreinte);

    /** Releves dont la periode couvre au moins un jour de l'intervalle, du plus recent au plus ancien. */
    @Query("""
            select r from Releve r
            where r.dateMin is not null and r.dateMin <= :au and r.dateMax >= :du
            order by r.creeLe desc""")
    List<Releve> couvrant(@Param("du") LocalDate du, @Param("au") LocalDate au);
}
