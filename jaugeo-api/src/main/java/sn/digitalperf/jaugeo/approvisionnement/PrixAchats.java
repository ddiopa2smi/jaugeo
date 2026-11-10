package sn.digitalperf.jaugeo.approvisionnement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;

/** Interface publique de l'approvisionnement pour le calcul des marges (compte d'exploitation, vue Direction). */
public interface PrixAchats {

    /**
     * Prix d'achat convenus d'un produit (identifiant technique), par date d'effet : ceux du fournisseur principal
     * s'il en a pour ce produit, sinon ceux de tous les fournisseurs (le dernier saisi l'emporte a date egale).
     * Le prix en vigueur un jour donne est celui de la plus grande date d'effet anterieure ou egale.
     */
    NavigableMap<LocalDate, BigDecimal> prixConvenus(long produitId);
}
