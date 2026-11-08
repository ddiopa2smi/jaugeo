package sn.digitalperf.jaugeo.reprise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disposition d'une feuille station du classeur « SUIVI DES STATIONS 2026 » : colonne de chaque grandeur.
 * Les feuilles n'ont pas toutes la meme disposition (Niandane : deux produits et quatre pistolets ; Hydrobase :
 * bons a recouvrer par la direction et par le gerant). Une colonne nulle = grandeur absente de la feuille.
 *
 * @param pistolets       code du pistolet dans Jaugeo -> colonnes [index depart, index arrivee] et produit
 * @param toutACredit     toute la vente du jour est un bon pour le client de la station (Hydrobase : Cheikh Omar),
 *                        et chaque versement est un recouvrement de ce client
 * @param colonneCreances colonne « CREANCES » saisie a la main (Potou) : signalee, jamais importee
 */
public record Disposition(String type, Map<String, Pistolet> pistolets, Map<String, Produit> produits,
                          String montantVentes, String aVerser, String versement, String referenceVersement,
                          String depenses, String colonneCreances, String ecartCaisse, String referenceBl,
                          List<String> observations, boolean toutACredit) {

    /** Colonnes d'un pistolet : index de depart, index d'arrivee. */
    public record Pistolet(String produit, String depart, String arrivee) {
    }

    /** Colonnes propres a un produit ; jauges = une ou deux colonnes additionnees. */
    public record Produit(String remise, String litres, String prix, String bonsDirection, String bonsGerant,
                          String complements, String stockDepart, String reception, String theorique,
                          List<String> jauges, String ecartStock) {
    }

    static final String GASOIL = "GASOIL";
    static final String SUPER = "SUPER";
    static final String ESSENCE = "ESSENCE_PIROGUE";

    /** Niandane : gasoil et super, pistolets G1/S1 (pompe 1) et G2/S2 (pompe 2). */
    static Disposition niandane() {
        Map<String, Pistolet> p = new LinkedHashMap<>();
        p.put("G1", new Pistolet(GASOIL, "B", "F"));
        p.put("G2", new Pistolet(GASOIL, "C", "G"));
        p.put("S1", new Pistolet(SUPER, "D", "H"));
        p.put("S2", new Pistolet(SUPER, "E", "I"));
        Map<String, Produit> pr = new LinkedHashMap<>();
        pr.put(GASOIL, new Produit("L", "N", "O", "V", null, "Y", "AJ", "AM", "AR", List.of("AU"), "AY"));
        pr.put(SUPER, new Produit("M", "Q", "R", "W", null, "Z", "AK", "AN", "AS", List.of("AW"), "BA"));
        return new Disposition("NIANDANE", p, pr, "U", "AB", "AC", "AF", "AD", null, "AE", "AL",
                List.of("AG", "BD"), false);
    }

    /** Hydrobase : toute la vente au credit de Cheikh Omar, versements = recouvrements. */
    static Disposition hydrobase() {
        Produit e = new Produit("G", "H", "I", "K", "M", "O", "Z", "AB", "AE", List.of("AG", "AH"), "AJ");
        return new Disposition("HYDROBASE", peche(), Map.of(ESSENCE, e), "J", null, "R", "V", "T", null, "U", "AA",
                List.of("W", "AM"), true);
    }

    /** Stations peche standard (Kayar, Yoff, Mbour) ; Potou a une colonne « CREANCES » a la place des depenses. */
    static Disposition standard(boolean colonneCreances) {
        Produit e = new Produit("G", "H", "I", "K", null, "M", "W", "Y", "AB", List.of("AD", "AE"), "AG");
        return new Disposition(colonneCreances ? "POTOU" : "STANDARD", peche(), Map.of(ESSENCE, e), "J", "O", "P", "S",
                colonneCreances ? null : "Q", colonneCreances ? "Q" : null, "R", "X", List.of("T", "AJ"), false);
    }

    private static Map<String, Pistolet> peche() {
        Map<String, Pistolet> p = new LinkedHashMap<>();
        p.put("E1", new Pistolet(ESSENCE, "B", "D"));
        p.put("E2", new Pistolet(ESSENCE, "C", "E"));
        return p;
    }

    /** Disposition par type (option --feuille des tests et des classeurs futurs). */
    static Disposition parType(String type) {
        return switch (type.toUpperCase()) {
            case "NIANDANE" -> niandane();
            case "HYDROBASE" -> hydrobase();
            case "POTOU" -> standard(true);
            case "STANDARD" -> standard(false);
            default -> throw new IllegalArgumentException("Disposition inconnue : " + type
                    + " (NIANDANE, HYDROBASE, POTOU ou STANDARD)");
        };
    }
}
