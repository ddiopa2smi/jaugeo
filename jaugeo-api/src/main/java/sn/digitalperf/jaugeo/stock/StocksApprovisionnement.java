package sn.digitalperf.jaugeo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Interface publique du stock pour l'approvisionnement : etat des cuves et receptions des livraisons. */
public interface StocksApprovisionnement {

    /**
     * Dernier stock fige d'une cuve en service (journee cloturee) et ventes moyennes des 7 jours precedents
     * (nulles sans historique).
     */
    record EtatCuve(long stationId, long cuveId, String cuveCode, long produitId, BigDecimal capaciteLitres,
                    BigDecimal creuxSecuriteLitres, BigDecimal seuilReapproLitres, LocalDate date,
                    BigDecimal stockPhysique, BigDecimal ventesMoyennes) {
    }

    /**
     * Reception d'un BL. Quantites livree et mesuree dans la meme base (15 °C si le BL et les releves le
     * permettent, ambiante sinon) ; manquant = livree - mesuree, positif ou nul ; complement fige a la cloture.
     */
    record ReceptionRef(long id, Long livraisonId, long journeeId, long stationId, long produitId, LocalDate date,
                        String numeroBl, String fournisseur, String transporteur, BigDecimal quantiteBl,
                        BigDecimal quantiteBl15, BigDecimal quantiteRecue, boolean compareeA15,
                        BigDecimal quantiteLivree, BigDecimal quantiteMesuree, BigDecimal manquant,
                        String imputation, BigDecimal complementLitres, String complementStatut) {
    }

    /** Cuves en service de toutes les stations, sans filtrage par perimetre. */
    List<EtatCuve> etatCuves();

    List<ReceptionRef> receptionsDesLivraisons(Collection<Long> livraisonIds);

    List<ReceptionRef> receptionsDeLaJournee(long journeeId);

    Optional<ReceptionRef> receptionParBl(String fournisseur, String numeroBl);
}
