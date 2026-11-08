package sn.digitalperf.jaugeo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Garde-fou d'architecture : aucun cycle entre modules, aucun acces aux classes internes
 * d'un autre module. Genere aussi la documentation des modules (target/spring-modulith-docs).
 */
class ArchitectureModulaireTest {

    private final ApplicationModules modules = ApplicationModules.of(JaugeoApiApplication.class);

    @Test
    void lesModulesRespectentLeursFrontieres() {
        modules.verify();
    }

    @Test
    void documenterLesModules() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
