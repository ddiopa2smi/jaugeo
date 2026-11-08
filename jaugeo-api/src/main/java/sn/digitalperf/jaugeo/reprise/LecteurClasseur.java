package sn.digitalperf.jaugeo.reprise;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import sn.digitalperf.jaugeo.reprise.Classeur.Attendu;
import sn.digitalperf.jaugeo.reprise.Classeur.Constat;
import sn.digitalperf.jaugeo.reprise.Classeur.Depot;
import sn.digitalperf.jaugeo.reprise.Classeur.Feuille;
import sn.digitalperf.jaugeo.reprise.Classeur.Jour;
import sn.digitalperf.jaugeo.reprise.Classeur.LigneAnalyse;
import sn.digitalperf.jaugeo.reprise.Classeur.Reception;

/**
 * Lecture du classeur « SUIVI DES STATIONS » : valeurs saisies (index, jauges, livraisons, versements...) et valeurs
 * calculees par les formules (gardees en cache par Excel), qui servent au rapprochement.
 *
 * <p>Les feuilles station commencent ligne 4, une ligne par jour. Une date presente deux fois (Niandane, 19/02/2026)
 * est fusionnee en une journee : index d'arrivee et jauges de la derniere ligne, volumes et montants additionnes.
 */
public final class LecteurClasseur {

    /** Feuille station -> code station et disposition. */
    public record Affectation(String station, Disposition disposition) {
    }

    /** Feuilles du classeur 2026 du client. */
    public static Map<String, Affectation> feuillesParDefaut() {
        Map<String, Affectation> m = new LinkedHashMap<>();
        m.put("STATION NIADANE", new Affectation("NIA", Disposition.niandane()));
        m.put("STATION HYDROBASE", new Affectation("HYD", Disposition.hydrobase()));
        m.put("STATION CAYAR", new Affectation("KAY", Disposition.standard(false)));
        m.put("STATION POTOU", new Affectation("POT", Disposition.standard(true)));
        m.put("STATION YOFF", new Affectation("YOF", Disposition.standard(false)));
        m.put("STATION MBOUR", new Affectation("MBO", Disposition.standard(false)));
        return m;
    }

    /** Libelles des stations dans les feuilles d'analyse, quand ils different du nom de la feuille. */
    public static Map<String, String> aliasParDefaut() {
        return Map.of("STATION NIANDANE", "NIA", "STATION KAYAR", "KAY");
    }

    private static final int PREMIERE_LIGNE = 4;
    private static final Pattern DATE_BORDEREAU = Pattern.compile("DU\\s*(\\d{1,2})/(\\d{1,2})/(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERO_BORDEREAU = Pattern.compile("N\\D{0,3}?(\\d{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANQUE = Pattern.compile("(B\\.?A\\.?T|L\\.?B\\.?A|B\\.?I\\.?S|C\\.?B)\\.?\\s*\\)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOIS_DU = Pattern.compile("DU\\s*01/(\\d{2})/(\\d{4})");
    private static final Pattern MOIS_NOM = Pattern.compile("MOIS DE\\s+([A-ZÉÛ]+)\\s+(\\d{4})");
    private static final List<String> MOIS = List.of("JANVIER", "FEVRIER", "MARS", "AVRIL", "MAI", "JUIN", "JUILLET",
            "AOUT", "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DECEMBRE");

    private final Map<String, Affectation> feuilles;
    private final Map<String, String> alias;
    private final List<Constat> constats = new ArrayList<>();
    /** Reference de versement lue sur une ligne sans montant (rattachee ensuite au montant qui la precede). */
    private String orpheline;

    public LecteurClasseur(Map<String, Affectation> feuilles, Map<String, String> alias) {
        this.feuilles = feuilles;
        this.alias = alias;
    }

    public Classeur lire(InputStream fichier) throws IOException {
        try (Workbook wb = WorkbookFactory.create(fichier)) {
            Map<String, Feuille> lues = new LinkedHashMap<>();
            List<LigneAnalyse> analyses = new ArrayList<>();
            for (Sheet s : wb) {
                String nom = normaliser(s.getSheetName());
                Affectation a = feuilles.get(nom);
                if (a != null) {
                    lues.put(a.station(), lireStation(s, nom, a));
                } else if (nom.startsWith("ANALYSE VENTES")) {
                    analyses.addAll(lireAnalyse(s));
                }
            }
            return new Classeur(lues, analyses, List.copyOf(constats));
        }
    }

    // ---------------------------------------------------------------- Feuilles station

    private Feuille lireStation(Sheet s, String nom, Affectation a) {
        Disposition d = a.disposition();
        String station = a.station();
        Map<String, BigDecimal> stockDepart = new LinkedHashMap<>();
        d.produits().forEach((produit, cols) -> stockDepart.put(produit, nombre(s, cols.stockDepart(), PREMIERE_LIGNE)));

        List<Jour> jours = new ArrayList<>();
        for (int ligne = PREMIERE_LIGNE; ligne <= s.getLastRowNum() + 1; ligne++) {
            LocalDate date = date(cellule(s, "A", ligne));
            if (date == null) {
                break;
            }
            orpheline = null;
            Jour j = lireJour(s, d, station, ligne, date);
            if (orpheline != null) {
                rattacher(jours, j, station, ligne, orpheline);
            }
            Jour precedent = jours.isEmpty() ? null : jours.get(jours.size() - 1);
            if (precedent != null && precedent.date().equals(date)) {
                constats.add(new Constat(station, date, precedent.libelleLignes() + " et " + ligne, "DATE_EN_DOUBLE",
                        "La date figure sur deux lignes : les deux lignes forment une seule journée (index d'arrivée et "
                                + "jauges de la seconde, ventes et mouvements additionnés)."));
                jours.set(jours.size() - 1, fusionner(precedent, j));
            } else {
                if (precedent != null && !date.equals(precedent.date().plusDays(1))) {
                    constats.add(new Constat(station, date, "ligne " + ligne, "DATE_NON_CONSECUTIVE",
                            "La ligne précédente est datée du " + precedent.date() + "."));
                }
                jours.add(j);
            }
        }
        return new Feuille(nom, station, d, stockDepart, jours);
    }

    private Jour lireJour(Sheet s, Disposition d, String station, int ligne, LocalDate date) {
        Map<String, BigDecimal> departs = new LinkedHashMap<>();
        Map<String, BigDecimal> arrivees = new LinkedHashMap<>();
        d.pistolets().forEach((code, p) -> {
            departs.put(code, nombre(s, p.depart(), ligne));
            arrivees.put(code, nombre(s, p.arrivee(), ligne));
        });
        Map<String, BigDecimal> prix = new LinkedHashMap<>();
        Map<String, BigDecimal> remises = new LinkedHashMap<>();
        Map<String, BigDecimal> jauges = new LinkedHashMap<>();
        Map<String, BigDecimal> bonsDirection = new LinkedHashMap<>();
        Map<String, BigDecimal> bonsGerant = new LinkedHashMap<>();
        Map<String, BigDecimal> complements = new LinkedHashMap<>();
        Map<String, BigDecimal> litres = new LinkedHashMap<>();
        Map<String, BigDecimal> theorique = new LinkedHashMap<>();
        Map<String, BigDecimal> ecartStock = new LinkedHashMap<>();
        List<String> livres = new ArrayList<>();
        Map<String, BigDecimal> livraisons = new LinkedHashMap<>();
        d.produits().forEach((produit, c) -> {
            prix.put(produit, nombre(s, c.prix(), ligne));
            remises.put(produit, positif(nombre(s, c.remise(), ligne)));
            BigDecimal jauge = null;
            for (String col : c.jauges()) {
                BigDecimal v = nombre(s, col, ligne);
                if (v != null) {
                    jauge = jauge == null ? v : jauge.add(v);
                }
            }
            jauges.put(produit, jauge);
            bonsDirection.put(produit, positif(nombre(s, c.bonsDirection(), ligne)));
            bonsGerant.put(produit, positif(nombre(s, c.bonsGerant(), ligne)));
            complements.put(produit, positif(nombre(s, c.complements(), ligne)));
            litres.put(produit, zero(nombre(s, c.litres(), ligne)));
            theorique.put(produit, nombre(s, c.theorique(), ligne));
            ecartStock.put(produit, zero(nombre(s, c.ecartStock(), ligne)));
            BigDecimal recu = positif(nombre(s, c.reception(), ligne));
            if (recu.signum() > 0) {
                livraisons.put(produit, recu);
            }
        });
        List<Reception> receptions = receptions(station, date, ligne, texte(s, d.referenceBl(), ligne), livraisons);

        List<Depot> versements = new ArrayList<>();
        BigDecimal montantVerse = positif(nombre(s, d.versement(), ligne));
        String reference = texte(s, d.referenceVersement(), ligne);
        if (montantVerse.signum() > 0) {
            versements.add(depot(montantVerse, reference));
        } else if (reference != null) {
            orpheline = reference;
        }
        BigDecimal creances = d.colonneCreances() == null ? null : nombre(s, d.colonneCreances(), ligne);
        if (creances != null && creances.signum() != 0) {
            constats.add(new Constat(station, date, "ligne " + ligne, "CREANCE_MANUELLE",
                    "Montant saisi dans la colonne CREANCES (" + Rapport.nombre(creances)
                            + " F) : sans équivalent dans Jaugeo, non importé ; à régulariser par un arrêté de caisse."));
        }
        List<String> notes = new ArrayList<>();
        for (String col : d.observations()) {
            String t = texte(s, col, ligne);
            if (t != null) {
                notes.add(t);
            }
        }
        Attendu attendu = new Attendu(litres, nombre(s, d.montantVentes(), ligne), nombre(s, d.aVerser(), ligne),
                theorique, ecartStock, nombre(s, d.ecartCaisse(), ligne));
        return new Jour(date, List.of(ligne), departs, arrivees, prix, remises, jauges, receptions, bonsDirection,
                bonsGerant, complements, versements, positif(nombre(s, d.depenses(), ligne)), creances,
                notes.isEmpty() ? null : String.join(" — ", notes), attendu);
    }

    /**
     * Reference de bordereau inscrite quelques lignes apres son montant (Hydrobase, Potou) : rattachee au dernier
     * versement sans reference des 4 jours precedents.
     */
    private void rattacher(List<Jour> jours, Jour courant, String station, int ligne, String reference) {
        for (int k = jours.size() - 1; k >= 0 && !jours.get(k).date().isBefore(courant.date().minusDays(4)); k--) {
            Jour x = jours.get(k);
            for (int i = 0; i < x.versements().size(); i++) {
                Depot v = x.versements().get(i);
                if (v.reference() == null) {
                    List<Depot> versements = new ArrayList<>(x.versements());
                    versements.set(i, depot(v.montant(), reference));
                    jours.set(k, avecVersements(x, versements));
                    constats.add(new Constat(station, x.date(), x.libelleLignes(), "REFERENCE_DECALEE", "Versement de "
                            + Rapport.nombre(v.montant()) + " F sans référence ; la référence « " + reference
                            + " » est inscrite ligne " + ligne + " sans montant : les deux sont rapprochés."));
                    return;
                }
            }
        }
        constats.add(new Constat(station, courant.date(), "ligne " + ligne, "REFERENCE_SANS_MONTANT",
                "Référence de versement sans montant (« " + reference + " ») et aucun versement sans référence dans les "
                        + "4 jours qui précèdent : ignorée."));
    }

    private static Jour avecVersements(Jour j, List<Depot> versements) {
        return new Jour(j.date(), j.lignes(), j.departs(), j.arrivees(), j.prix(), j.remises(), j.jauges(), j.receptions(),
                j.bonsDirection(), j.bonsGerant(), j.complements(), versements, j.depenses(), j.creances(), j.observation(),
                j.attendu());
    }

    /** Une reference « BL1/BL2 » pour deux produits livres le meme jour : un BL par produit, dans l'ordre. */
    private List<Reception> receptions(String station, LocalDate date, int ligne, String reference,
                                       Map<String, BigDecimal> livraisons) {
        List<Reception> r = new ArrayList<>();
        if (livraisons.isEmpty()) {
            return r;
        }
        List<String> numeros = reference == null ? List.of() : List.of(reference.split("\\s*/\\s*"));
        int i = 0;
        for (Map.Entry<String, BigDecimal> e : livraisons.entrySet()) {
            String numero;
            if (numeros.size() == livraisons.size()) {
                numero = numeros.get(i);
            } else if (numeros.size() == 1) {
                numero = livraisons.size() == 1 ? numeros.get(0) : numeros.get(0) + "-" + e.getKey().substring(0, 2);
            } else {
                numero = "SANS-BL-" + station + "-" + date.toString().replace("-", "") + (livraisons.size() > 1 ? "-" + e.getKey().substring(0, 2) : "");
                constats.add(new Constat(station, date, "ligne " + ligne, "BL_SANS_REFERENCE",
                        "Livraison de " + Rapport.nombre(e.getValue()) + " L sans référence de BL exploitable ("
                                + (reference == null ? "vide" : "« " + reference + " »") + ") : BL « " + numero + " »."));
            }
            r.add(new Reception(numero.trim().toUpperCase(Locale.ROOT), e.getKey(), e.getValue()));
            i++;
        }
        return r;
    }

    static Depot depot(BigDecimal montant, String reference) {
        if (reference == null) {
            return new Depot(montant, null, null, null, null);
        }
        LocalDate date = null;
        Matcher m = DATE_BORDEREAU.matcher(reference);
        if (m.find()) {
            try {
                date = LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            } catch (java.time.DateTimeException e) {
                date = null;
            }
        }
        Matcher n = NUMERO_BORDEREAU.matcher(reference);
        String numero = n.find() ? n.group(1) : null;
        Matcher b = BANQUE.matcher(reference.trim());
        String banque = b.find() ? b.group(1).replace(".", "").toUpperCase(Locale.ROOT) : null;
        return new Depot(montant, reference.trim(), numero, banque, date);
    }

    private static Jour fusionner(Jour a, Jour b) {
        List<Integer> lignes = new ArrayList<>(a.lignes());
        lignes.addAll(b.lignes());
        List<Reception> receptions = new ArrayList<>(a.receptions());
        receptions.addAll(b.receptions());
        List<Depot> versements = new ArrayList<>(a.versements());
        versements.addAll(b.versements());
        String observation = a.observation() == null ? b.observation()
                : b.observation() == null ? a.observation() : a.observation() + " — " + b.observation();
        Attendu x = a.attendu();
        Attendu y = b.attendu();
        Attendu attendu = new Attendu(somme(x.litres(), y.litres()), somme(x.ventes(), y.ventes()),
                somme(x.aVerser(), y.aVerser()), y.theorique(), y.ecartStock(), y.ecartCaisse());
        return new Jour(a.date(), lignes, a.departs(), b.arrivees(), a.prix(), somme(a.remises(), b.remises()),
                b.jauges(), receptions, somme(a.bonsDirection(), b.bonsDirection()), somme(a.bonsGerant(), b.bonsGerant()),
                somme(a.complements(), b.complements()), versements, a.depenses().add(b.depenses()),
                somme(a.creances(), b.creances()), observation, attendu);
    }

    // ---------------------------------------------------------------- Feuille d'analyse

    private List<LigneAnalyse> lireAnalyse(Sheet s) {
        List<LigneAnalyse> r = new ArrayList<>();
        YearMonth mois = null;
        for (Row row : s) {
            int ligne = row.getRowNum() + 1;
            String a = texte(s, "A", ligne);
            if (a == null) {
                continue;
            }
            String titre = normaliser(a);
            if (titre.startsWith("ANALYSE DES VENTES")) {
                mois = moisDuTitre(titre);
                continue;
            }
            if (mois == null || !titre.startsWith("STATION ")) {
                continue;
            }
            String station = alias.getOrDefault(titre, feuilles.containsKey(titre) ? feuilles.get(titre).station() : null);
            if (station == null) {
                constats.add(new Constat(null, mois.atDay(1), "analyse ligne " + ligne, "STATION_INCONNUE",
                        "Ligne « " + a + " » de la feuille d'analyse : station non reconnue."));
                continue;
            }
            Map<String, BigDecimal> litres = new LinkedHashMap<>();
            litres.put(Disposition.GASOIL, zero(nombre(s, "B", ligne)));
            litres.put(Disposition.SUPER, zero(nombre(s, "C", ligne)));
            litres.put(Disposition.ESSENCE, zero(nombre(s, "D", ligne)));
            r.add(new LigneAnalyse(mois, station, litres, nombre(s, "E", ligne), nombre(s, "F", ligne),
                    nombre(s, "G", ligne), nombre(s, "H", ligne), nombre(s, "I", ligne), nombre(s, "J", ligne),
                    nombre(s, "K", ligne), nombre(s, "L", ligne), nombre(s, "M", ligne), nombre(s, "R", ligne),
                    texte(s, "S", ligne)));
        }
        return r;
    }

    static YearMonth moisDuTitre(String titre) {
        Matcher m = MOIS_DU.matcher(titre);
        if (m.find()) {
            return YearMonth.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        Matcher n = MOIS_NOM.matcher(titre);
        if (n.find()) {
            int i = MOIS.indexOf(n.group(1).replace("É", "E").replace("Û", "U"));
            if (i >= 0) {
                return YearMonth.of(Integer.parseInt(n.group(2)), i + 1);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- Cellules

    private static Cell cellule(Sheet s, String colonne, int ligne) {
        if (colonne == null) {
            return null;
        }
        Row row = s.getRow(ligne - 1);
        return row == null ? null : row.getCell(CellReference.convertColStringToIndex(colonne));
    }

    private static CellType type(Cell c) {
        return c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    }

    private static LocalDate date(Cell c) {
        if (c == null || type(c) != CellType.NUMERIC || !DateUtil.isCellDateFormatted(c)) {
            return null;
        }
        return c.getLocalDateTimeCellValue().toLocalDate();
    }

    /** Valeur numerique (saisie ou calculee) ; le texte « 0 » des formules SI(...;"0";...) vaut zero. */
    static BigDecimal nombre(Sheet s, String colonne, int ligne) {
        Cell c = cellule(s, colonne, ligne);
        if (c == null) {
            return null;
        }
        CellType t = type(c);
        if (t == CellType.NUMERIC) {
            return lisible(BigDecimal.valueOf(c.getNumericCellValue()));
        }
        if (t == CellType.STRING) {
            String v = c.getStringCellValue().trim().replace(" ", "").replace(',', '.');
            if (v.isEmpty()) {
                return null;
            }
            try {
                return lisible(new BigDecimal(v));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Deux decimales au plus, sans zeros inutiles ni exposant (10 et non 1E+1). */
    private static BigDecimal lisible(BigDecimal v) {
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0) : r;
    }

    private static String texte(Sheet s, String colonne, int ligne) {
        Cell c = cellule(s, colonne, ligne);
        if (c == null || type(c) != CellType.STRING) {
            return null;
        }
        String v = c.getStringCellValue().replace(' ', ' ').trim();
        return v.isEmpty() ? null : v;
    }

    static String normaliser(String nom) {
        return nom.replace(' ', ' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal positif(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    private static BigDecimal somme(BigDecimal a, BigDecimal b) {
        return a == null ? b : b == null ? a : a.add(b);
    }

    private static Map<String, BigDecimal> somme(Map<String, BigDecimal> a, Map<String, BigDecimal> b) {
        Map<String, BigDecimal> r = new LinkedHashMap<>(a);
        b.forEach((k, v) -> r.merge(k, v, LecteurClasseur::somme));
        return r;
    }

    static LocalDate dateIso(String v) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date attendue au format AAAA-MM-JJ : " + v);
        }
    }
}
