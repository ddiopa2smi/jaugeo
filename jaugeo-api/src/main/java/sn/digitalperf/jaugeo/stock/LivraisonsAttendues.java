package sn.digitalperf.jaugeo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Point d'extension implemente par l'approvisionnement (M8) : livraisons des tournees citernes attendues a une
 * station, auxquelles le gerant rattache sa reception, et tolerance de manquant du transporteur. Le stock n'en
 * depend pas : sans implementation, aucune livraison n'est attendue et la tolerance du reseau s'applique.
 */
public interface LivraisonsAttendues {

    /** Livraison d'une tournee : BL et quantites connus des le chargement au depot. */
    record LivraisonAttendue(long id, UUID uuid, String tourneeNumero, String tourneeStatut, long stationId,
                             long produitId, BigDecimal quantiteCommandee, String numeroBl, BigDecimal quantiteBl,
                             BigDecimal quantiteBl15, String fournisseur, String transporteur, String chauffeur,
                             String immatriculation, String scelles, LocalDate dateLivraisonPrevue) {
    }

    /** Livraisons non encore receptionnees des tournees en cours (non annulees) vers cette station. */
    List<LivraisonAttendue> attendues(long stationId);

    Optional<LivraisonAttendue> livraison(UUID uuid);

    Optional<LivraisonAttendue> livraison(long id);

    /**
     * Tolerance de manquant (% de la quantite livree) du transporteur de la livraison rattachee, ou a defaut du
     * transporteur dont le nom a ete saisi ; vide si le transporteur n'a pas de tolerance propre.
     */
    Optional<BigDecimal> tolerancePct(Long livraisonId, String transporteur);

    /**
     * Noms des fournisseurs (depots) actifs declares par la logistique, par ordre alphabetique : le gerant choisit
     * dans cette liste le fournisseur d'une reception hors tournee.
     */
    default List<String> fournisseurs() {
        return List.of();
    }
}
