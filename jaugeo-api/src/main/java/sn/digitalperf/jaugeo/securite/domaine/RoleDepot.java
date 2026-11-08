package sn.digitalperf.jaugeo.securite.domaine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDepot extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findAllByOrderByLibelleAsc();
}
