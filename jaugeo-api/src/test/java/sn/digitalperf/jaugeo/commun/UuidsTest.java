package sn.digitalperf.jaugeo.commun;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidsTest {

    @Test
    void genereDesUuidVersion7UniquesEtOrdonnesDansLeTemps() throws InterruptedException {
        UUID premier = Uuids.v7();
        Thread.sleep(2);
        UUID second = Uuids.v7();
        assertThat(premier.version()).isEqualTo(7);
        assertThat(premier.variant()).isEqualTo(2);
        assertThat(premier.toString().compareTo(second.toString())).isNegative();

        Set<UUID> tous = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tous.add(Uuids.v7());
        }
        assertThat(tous).hasSize(10_000);
    }
}
