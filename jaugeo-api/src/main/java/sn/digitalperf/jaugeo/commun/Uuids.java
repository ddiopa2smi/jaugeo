package sn.digitalperf.jaugeo.commun;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generation d'UUID version 7 (RFC 9562) : ordonnes dans le temps, comme la fonction
 * uuidv7() de PostgreSQL 18, ce qui garde les index compacts.
 */
public final class Uuids {

    private static final SecureRandom ALEA = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        long millis = System.currentTimeMillis();
        byte[] octets = new byte[16];
        ALEA.nextBytes(octets);
        for (int i = 0; i < 6; i++) {
            octets[i] = (byte) (millis >>> (40 - 8 * i));
        }
        octets[6] = (byte) ((octets[6] & 0x0F) | 0x70); // version 7
        octets[8] = (byte) ((octets[8] & 0x3F) | 0x80); // variante RFC
        long haut = 0;
        long bas = 0;
        for (int i = 0; i < 8; i++) {
            haut = (haut << 8) | (octets[i] & 0xFF);
            bas = (bas << 8) | (octets[i + 8] & 0xFF);
        }
        return new UUID(haut, bas);
    }
}
