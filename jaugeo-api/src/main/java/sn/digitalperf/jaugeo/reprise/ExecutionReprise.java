package sn.digitalperf.jaugeo.reprise;

import static sn.digitalperf.jaugeo.reprise.ApiJaugeo.corps;
import static sn.digitalperf.jaugeo.reprise.ApiJaugeo.decimal;
import static sn.digitalperf.jaugeo.reprise.ApiJaugeo.texte;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import sn.digitalperf.jaugeo.reprise.ApiJaugeo.Refus;
import sn.digitalperf.jaugeo.reprise.ApiJaugeo.Session;
import sn.digitalperf.jaugeo.reprise.Classeur.Depot;
import sn.digitalperf.jaugeo.reprise.Classeur.Feuille;
import sn.digitalperf.jaugeo.reprise.Classeur.Jour;
import sn.digitalperf.jaugeo.reprise.Classeur.LigneAnalyse;
import sn.digitalperf.jaugeo.reprise.Classeur.Reception;
import sn.digitalperf.jaugeo.reprise.Rapport.Comparaison;
import tools.jackson.databind.JsonNode;

/**
 * Reprise d'une periode du classeur par l'API, comme le feraient un gerant, un superviseur et un comptable :
 * Jaugeo recalcule tout (ventes, stocks, montants a verser) et reste seul juge des regles. Les comptes de reprise
 * sont crees pour l'occasion par l'administrateur, puis desactives.
 *
 * <p>Ce que le classeur fait autrement que Jaugeo, et que la reprise reproduit :
 * <ul>
 *   <li>Sorties calculees par produit avec plancher a zero : un index qui recule devient un changement de compteur,
 *       et le recul est deduit des autres pistolets du produit (remise en cuve), comme dans la formule SI(...&lt;0;0;...).</li>
 *   <li>Index de depart retape a la main : changement de compteur depuis l'index reporte.</li>
 *   <li>Compléments citerne : saisis le jour de la livraison, ils deviennent l'ecart entre la quantite du BL et
 *       la quantite recue.</li>
 *   <li>Hydrobase : toute la vente est un bon de Cheikh Omar ; chaque versement est un recouvrement de ce client.</li>
 *   <li>Jauge absente : stock physique = stock theorique (pas d'ecart invente).</li>
 *   <li>Versements dates du bordereau quand la reference porte une date plausible, sinon de la ligne du jour.</li>
 * </ul>
 */
final class ExecutionReprise {

    static final String FOURNISSEUR = "Non renseigné (reprise)";
    private static final DateTimeFormatter AAAAMMJJ = DateTimeFormatter.BASIC_ISO_DATE;
    private static final BigDecimal SEUIL_COMPLEMENT_PCT = new BigDecimal("0.3");
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** Station visee : identifiants Jaugeo et feuille du classeur. */
    private record Cible(String code, String uuid, long numero, LocalDate ouverture, Feuille feuille) {
    }

    /** Arret d'une station : la chaine des journees ne peut pas continuer. */
    private static final class Arret extends RuntimeException {
        Arret(String message) {
            super(message);
        }
    }

    private final ApiJaugeo api;
    private final Classeur classeur;
    private final RepriseClasseur.Options o;
    private final Rapport rapport;
    private final PrintStream journal;
    private final Map<String, String> clients = new HashMap<>();
    private Session admin;
    private Session gerant;
    private Session superviseur;
    private Session comptable;
    private String natureAutre;
    private final List<String> comptes = new ArrayList<>();
    /** Banques connues de l'application, lues au premier versement. */
    private Set<String> banquesActives;
    private Map<String, JsonNode> banquesInactives;

    ExecutionReprise(ApiJaugeo api, Classeur classeur, RepriseClasseur.Options o, Rapport rapport, PrintStream journal) {
        this.api = api;
        this.classeur = classeur;
        this.o = o;
        this.rapport = rapport;
        this.journal = journal;
    }

    void lancer() {
        admin = api.session(o.admin(), o.motDePasseAdmin());
        List<Cible> cibles = cibles();
        if (cibles.isEmpty()) {
            journal.println("Aucune station à reprendre.");
            return;
        }
        try {
            ouvrirComptes(cibles);
            for (Cible c : cibles) {
                station(c);
            }
            for (YearMonth m = YearMonth.from(o.du()); !m.isAfter(YearMonth.from(o.au())); m = m.plusMonths(1)) {
                rapprocherMois(m, cibles);
            }
        } finally {
            fermerComptes();
        }
    }

    // ---------------------------------------------------------------- Stations et comptes

    private List<Cible> cibles() {
        Map<String, JsonNode> parCode = new HashMap<>();
        admin.get("/api/v1/stations").forEach(s -> parCode.put(texte(s, "code"), s));
        List<Cible> r = new ArrayList<>();
        for (Feuille f : classeur.feuilles().values()) {
            if (!o.stations().isEmpty() && !o.stations().contains(f.station())) {
                continue;
            }
            JsonNode s = parCode.get(f.station());
            if (s == null) {
                rapport.constater(f.station(), null, null, "STATION_ABSENTE",
                        "La feuille « " + f.nom() + " » vise la station " + f.station() + ", absente de Jaugeo : non reprise.");
                continue;
            }
            String ouverture = texte(s, "dateOuverture");
            r.add(new Cible(f.station(), texte(s, "id"), s.path("numero").asLong(),
                    ouverture == null ? null : LocalDate.parse(ouverture), f));
        }
        return r;
    }

    private void ouvrirComptes(List<Cible> cibles) {
        String suffixe = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        List<Long> stations = cibles.stream().map(Cible::numero).toList();
        gerant = compte("reprise.gerant." + suffixe, "Reprise du classeur (gérant)", "GERANT", "STATIONS", stations);
        superviseur = compte("reprise.superviseur." + suffixe, "Reprise du classeur (superviseur)", "SUPERVISEUR", "RESEAU", List.of());
        comptable = compte("reprise.comptable." + suffixe, "Reprise du classeur (comptable)", "COMPTABLE", "RESEAU", List.of());
        journal.println("Comptes de reprise : " + String.join(", ", comptes));
    }

    private Session compte(String identifiant, String nom, String role, String perimetre, List<Long> stations) {
        JsonNode r = admin.post("/api/v1/utilisateurs", corps("identifiant", identifiant, "nomComplet", nom, "role", role,
                "perimetre", perimetre, "stationIds", stations));
        comptes.add(identifiant);
        Session s = api.session(identifiant, texte(r, "motDePasseProvisoire"));
        s.changerMotDePasse(motDePasse());
        return s;
    }

    /** Les comptes de reprise ne servent qu'une fois : desactives a la fin, meme apres un arret. */
    private void fermerComptes() {
        if (comptes.isEmpty()) {
            return;
        }
        try {
            for (JsonNode u : admin.get("/api/v1/utilisateurs")) {
                if (!comptes.contains(texte(u, "identifiant"))) {
                    continue;
                }
                admin.put("/api/v1/utilisateurs/" + texte(u, "id"), corps("nomComplet", texte(u, "nomComplet"),
                        "email", null, "telephone", null, "role", texte(u, "role"), "perimetre", texte(u, "perimetre"),
                        "stationIds", u.path("stationIds"), "actif", false, "version", u.path("version").asInt()));
            }
            journal.println("Comptes de reprise désactivés.");
        } catch (Refus e) {
            journal.println("Comptes de reprise à désactiver à la main (" + e.getMessage() + ") : " + String.join(", ", comptes));
        }
    }

    private static String motDePasse() {
        String alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder("Rep-");
        for (int i = 0; i < 20; i++) {
            b.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return b.append("-9z").toString();
    }

    // ---------------------------------------------------------------- Une station

    private void station(Cible c) {
        Feuille f = c.feuille();
        List<Jour> jours = f.jours().stream()
                .filter(j -> !j.date().isBefore(o.du()) && !j.date().isAfter(o.au()))
                .filter(j -> c.ouverture() == null || !j.date().isBefore(c.ouverture()))
                .toList();
        Rapport.BilanStation bilan = rapport.bilan(c.code());
        if (jours.isEmpty()) {
            bilan.arret = "Aucune journée de la période dans la feuille.";
            return;
        }
        JsonNode prochaine = gerant.get("/api/v1/stations/" + c.uuid() + "/journees").path("prochaine");
        String attendue = texte(prochaine, "date");
        LocalDate premiere = jours.get(0).date();
        if (attendue == null) {
            initialiser(c, premiere);
        } else if (!LocalDate.parse(attendue).equals(premiere)) {
            bilan.arret = "La prochaine journée à ouvrir dans Jaugeo est celle du " + attendue
                    + " : la reprise doit commencer à cette date (déjà importé ou trou dans la période).";
            journal.println(c.code() + " : " + bilan.arret);
            return;
        }
        Jour veille = f.jours().stream().filter(j -> j.date().isBefore(premiere)).reduce((a, b) -> b).orElse(null);
        journal.println(c.code() + " : reprise du " + premiere + " au " + jours.get(jours.size() - 1).date() + " (" + jours.size() + " journées)");
        for (Jour j : jours) {
            int rang = f.jours().indexOf(j);
            Jour suivant = rang + 1 < f.jours().size() ? f.jours().get(rang + 1) : null;
            try {
                journee(c, j, veille, suivant);
            } catch (Arret | Refus e) {
                String code = e instanceof Refus r ? r.code() + " : " : "";
                bilan.arret = "Journée du " + j.date() + " (" + j.libelleLignes() + ") : " + code + e.getMessage();
                journal.println(c.code() + " : arrêt — " + bilan.arret);
                return;
            }
            veille = j;
        }
        journal.println(c.code() + " : " + bilan.journeesConcordantes() + "/" + bilan.journees() + " journées concordantes");
    }

    /** Premiere reprise d'une station : solde de caisse et creances d'ouverture lus dans la feuille d'analyse. */
    private void initialiser(Cible c, LocalDate premiere) {
        LocalDate veille = premiere.minusDays(1);
        Optional<LigneAnalyse> a = analyse(c.code(), YearMonth.from(premiere));
        if (a.isEmpty()) {
            rapport.constater(c.code(), veille, null, "OUVERTURE_INCONNUE", "Pas de ligne dans la feuille d'analyse du mois : "
                    + "solde de caisse et créances d'ouverture non repris.");
            return;
        }
        LigneAnalyse l = a.get();
        if (positif(l.soldeOuverture())) {
            superviseur.post("/api/v1/stations/" + c.uuid() + "/arretes", corps("date", veille.toString(), "type", "REPRISE",
                    "especesComptees", l.soldeOuverture(), "responsable", "Reprise du classeur",
                    "observation", "Solde de caisse d'ouverture repris de la feuille d'analyse."));
            deposerCaisseOuverture(c, premiere, l);
        }
        if (positif(l.creancesDebut())) {
            comptable.post("/api/v1/clients/" + client(c, "GERANT") + "/operations", corps("stationId", c.uuid(),
                    "type", "REPRISE", "date", veille.toString(), "montant", l.creancesDebut(),
                    "reference", "Créances au " + veille + " (classeur)", "observation", court(l.observation(), 500)));
        }
    }

    /**
     * L'analyse du premier mois compte parmi ses versements le depot de la caisse d'ouverture, absent des feuilles
     * station : sans lui la caisse de Jaugeo garderait ce solde tout le mois. Le depot est reconstitue (au plus le
     * solde d'ouverture) le premier jour, pour la difference entre l'analyse et les lignes du mois.
     */
    private void deposerCaisseOuverture(Cible c, LocalDate premiere, LigneAnalyse l) {
        if (!positif(l.versements())) {
            return;
        }
        YearMonth mois = YearMonth.from(premiere);
        BigDecimal feuilles = c.feuille().jours().stream().filter(j -> YearMonth.from(j.date()).equals(mois))
                .flatMap(j -> j.versements().stream()).map(Depot::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal depot = l.versements().subtract(feuilles).min(l.soldeOuverture());
        if (depot.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return;
        }
        String reference = "R-" + c.code() + "-OUVERTURE";
        try {
            verser(c, premiere, "NON PRÉCISÉE", reference, depot, "Reprise : dépôt de la caisse d'ouverture compté dans "
                    + "les versements de l'analyse, absent des feuilles station.");
            rapport.constater(c.code(), premiere, null, "CAISSE_OUVERTURE_VERSEE", "Versements de l'analyse ("
                    + Rapport.nombre(l.versements()) + " F) supérieurs aux lignes du mois (" + Rapport.nombre(feuilles)
                    + " F) : dépôt de " + Rapport.nombre(depot) + " F de la caisse d'ouverture enregistré le " + premiere
                    + " (" + reference + ").");
        } catch (Refus e) {
            rapport.constater(c.code(), premiere, null, "CAISSE_OUVERTURE_NON_VERSEE", "Dépôt de " + Rapport.nombre(depot)
                    + " F de la caisse d'ouverture refusé (" + e.code() + " : " + e.getMessage() + ").");
        }
    }

    // ---------------------------------------------------------------- Une journee

    private void journee(Cible c, Jour j, Jour veille, Jour suivant) {
        Disposition d = c.feuille().disposition();
        JsonNode det = gerant.post("/api/v1/stations/" + c.uuid() + "/journees", corps("date", j.date().toString()));
        String id = texte(det, "id");
        String base = "/api/v1/journees/" + id;
        Instant debut = Instant.parse(texte(det, "debut"));

        // Index : changements de compteur et remises comme le classeur
        det = gerant.put(base + "/releves", corps("version", det.path("version").asInt(),
                "observation", court(j.observation(), 500), "releves", releves(c, j, det, debut)));

        // Livraisons, jaugeages
        JsonNode stock = gerant.get(base + "/stock");
        Map<String, String> cuves = new LinkedHashMap<>();
        stock.path("cuves").forEach(x -> cuves.putIfAbsent(texte(x, "produitCode"), texte(x, "cuveId")));
        int version = stock.path("version").asInt();
        Set<String> completes = new HashSet<>();
        for (Reception r : j.receptions()) {
            String cuve = exiger(cuves.get(r.produit()), "Aucune cuve de " + r.produit() + " pour la livraison " + r.numeroBl());
            BigDecimal complement = completes.add(r.produit()) ? j.complements().getOrDefault(r.produit(), BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal bl = r.litres().add(complement);
            if (complement.signum() > 0 && complement.multiply(CENT).compareTo(bl.multiply(SEUIL_COMPLEMENT_PCT)) <= 0) {
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "COMPLEMENT_SOUS_SEUIL", "Complément de "
                        + Rapport.nombre(complement) + " L sur un BL de " + Rapport.nombre(bl) + " L : sous la tolérance de "
                        + SEUIL_COMPLEMENT_PCT + " %, Jaugeo ne le facture pas au transporteur.");
            }
            version = gerant.post(base + "/receptions", corps("version", version, "cuveId", cuve, "numeroBl", r.numeroBl(),
                    "fournisseur", FOURNISSEUR, "quantiteBl", bl, "quantiteRecue", r.litres(),
                    "observation", "Reprise du classeur, " + j.libelleLignes())).path("version").asInt();
        }
        j.complements().forEach((produit, litres) -> {
            if (litres.signum() > 0 && !completes.contains(produit)) {
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "COMPLEMENT_SANS_LIVRAISON", "Complément de "
                        + Rapport.nombre(litres) + " L de " + produit + " sans livraison le même jour : non repris (le "
                        + "complément naît d'un BL dans Jaugeo).");
            }
        });
        Set<String> relevees = jauger(c, j, veille, suivant, base, version);

        // Ventes a credit, recouvrements
        credit(c, j, d, base);
        // Depenses
        depenses(c, j, base);

        // Cloture par le gerant, validation par le superviseur
        det = gerant.get(base);
        det = gerant.post(base + "/cloture", corps("version", det.path("version").asInt()));
        det = superviseur.post(base + "/validation", corps("version", det.path("version").asInt()));

        versements(c, j);
        rapprocherJour(c, j, det, base, relevees);
    }

    /** Releves des pistolets ; un index qui recule ou un depart retape deviennent des changements de compteur. */
    private List<Map<String, Object>> releves(Cible c, Jour j, JsonNode det, Instant debut) {
        Map<String, Map<String, BigDecimal>> deltas = new LinkedHashMap<>();
        Map<String, BigDecimal> deficits = new HashMap<>();
        Map<String, String> ids = new HashMap<>();
        Map<String, BigDecimal> arrivees = new HashMap<>();
        for (JsonNode l : det.path("lignes")) {
            String code = texte(l, "pistoletCode");
            String produit = texte(l, "produitCode");
            String pistolet = texte(l, "pistoletId");
            if (!j.arrivees().containsKey(code)) {
                throw new Arret("le pistolet " + code + " n'a pas de colonne dans la feuille « " + c.feuille().nom() + " ».");
            }
            BigDecimal arrivee = j.arrivees().get(code);
            if (arrivee == null) {
                throw new Arret("index d'arrivée du pistolet " + code + " absent du classeur.");
            }
            BigDecimal courant = decimal(l, "indexDepart");
            BigDecimal departClasseur = j.departs().get(code);
            if (departClasseur != null && departClasseur.compareTo(courant) != 0) {
                compteur(pistolet, debut.plusSeconds(60), courant, departClasseur, "Reprise du classeur : index de départ du "
                        + "pistolet " + code + " retapé à " + Rapport.nombre(departClasseur) + " (index reporté " + Rapport.nombre(courant) + ").");
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "INDEX_DEPART_RETAPE", "Pistolet " + code
                        + " : index de départ " + Rapport.nombre(departClasseur) + " au lieu de l'arrivée de la veille "
                        + Rapport.nombre(courant) + " ; enregistré comme changement de compteur.");
                courant = departClasseur;
            }
            BigDecimal delta = arrivee.subtract(courant);
            if (delta.signum() < 0) {
                compteur(pistolet, debut.plusSeconds(120), courant, arrivee, "Reprise du classeur : index d'arrivée "
                        + Rapport.nombre(arrivee) + " inférieur au départ " + Rapport.nombre(courant) + ".");
                deficits.merge(produit, delta.negate(), BigDecimal::add);
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "INDEX_EN_RECUL", "Pistolet " + code + " : index "
                        + "d'arrivée " + Rapport.nombre(arrivee) + " inférieur au départ " + Rapport.nombre(courant)
                        + " (recul de " + Rapport.nombre(delta.negate()) + " L) ; enregistré comme changement de compteur.");
                delta = BigDecimal.ZERO;
            }
            deltas.computeIfAbsent(produit, k -> new LinkedHashMap<>()).put(code, delta);
            ids.put(code, pistolet);
            arrivees.put(code, arrivee);
        }

        Map<String, BigDecimal> remises = new HashMap<>();
        deltas.forEach((produit, parPistolet) -> {
            BigDecimal sorties = parPistolet.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deficit = deficits.getOrDefault(produit, BigDecimal.ZERO);
            BigDecimal compense = deficit.min(sorties);
            BigDecimal reste = j.remises().getOrDefault(produit, BigDecimal.ZERO).add(compense);
            if (compense.signum() > 0) {
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "RECUL_COMPENSE", "Le classeur calcule les sorties de "
                        + produit + " tous pistolets confondus (plancher à zéro) : le recul de " + Rapport.nombre(deficit)
                        + " L est déduit des autres pistolets par une remise en cuve de " + Rapport.nombre(compense) + " L.");
            }
            List<Map.Entry<String, BigDecimal>> tries = new ArrayList<>(parPistolet.entrySet());
            tries.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
            for (Map.Entry<String, BigDecimal> e : tries) {
                BigDecimal r = e.getValue().min(reste);
                if (r.signum() > 0) {
                    remises.put(e.getKey(), r);
                    reste = reste.subtract(r);
                }
            }
            if (reste.signum() > 0) {
                throw new Arret("remise en cuve de " + produit + " supérieure aux sorties des pistolets.");
            }
        });

        List<Map<String, Object>> r = new ArrayList<>();
        ids.forEach((code, pistolet) -> r.add(corps("pistoletId", pistolet, "indexArrivee", arrivees.get(code),
                "remiseCuve", remises.get(code))));
        return r;
    }

    private void compteur(String pistolet, Instant date, BigDecimal avant, BigDecimal apres, String motif) {
        admin.post("/api/v1/pistolets/" + pistolet + "/evenements", corps("type", "REMPLACEMENT_TOTALISATEUR", "date", date.toString(),
                "indexAvant", avant, "indexApres", apres, "intervenant", "Reprise du classeur", "commentaire", motif));
    }

    /**
     * Ouverture (premiere journee suivie) puis cloture ; jauge absente = stock theorique. Une jauge du soir plus basse
     * que les ventes du lendemain sans livraison rendrait le stock theorique du lendemain negatif (cloture refusee) :
     * le classeur l'ignore car il ne recale jamais son stock theorique sur la jauge. Le stock de cloture est alors
     * releve au minimum compatible avec les ventes du lendemain. Renvoie les produits ainsi releves.
     */
    private Set<String> jauger(Cible c, Jour j, Jour veille, Jour suivant, String base, int version) {
        Set<String> relevees = new HashSet<>();
        JsonNode stock = gerant.get(base + "/stock");
        List<Map<String, Object>> ouvertures = new ArrayList<>();
        for (JsonNode cuve : stock.path("cuves")) {
            if (cuve.path("ouvertureRequise").asBoolean()) {
                String produit = texte(cuve, "produitCode");
                BigDecimal v = veille == null ? c.feuille().stockDepart().get(produit)
                        : Objects.requireNonNullElse(veille.jauges().get(produit), veille.attendu().theorique().get(produit));
                if (v == null || v.signum() < 0) {
                    rapport.constater(c.code(), j.date(), j.libelleLignes(), "STOCK_DEPART_ABSENT",
                            "Stock de départ de " + produit + " absent du classeur : 0 L retenu.");
                    v = BigDecimal.ZERO;
                }
                ouvertures.add(corps("cuveId", texte(cuve, "cuveId"), "moment", "OUVERTURE", "volumeLitres", v,
                        "observation", "Stock de départ repris du classeur."));
            }
        }
        if (!ouvertures.isEmpty()) {
            stock = gerant.put(base + "/jaugeages", corps("version", version, "jaugeages", ouvertures));
            version = stock.path("version").asInt();
        }
        List<Map<String, Object>> clotures = new ArrayList<>();
        for (JsonNode cuve : stock.path("cuves")) {
            String produit = texte(cuve, "produitCode");
            BigDecimal jauge = j.jauges().get(produit);
            String observation = "Reprise du classeur, " + j.libelleLignes() + ".";
            if (jauge == null) {
                jauge = decimal(cuve, "stockTheorique");
                if (jauge == null) {
                    throw new Arret("stock théorique de " + produit + " inconnu : jaugeage impossible.");
                }
                observation = "Jauge absente du classeur (" + j.libelleLignes() + ") : stock physique pris égal au stock théorique.";
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "JAUGE_ABSENTE", "Jauge de " + produit
                        + " absente : stock physique pris égal au stock théorique (" + Rapport.nombre(jauge) + " L).");
            }
            BigDecimal besoin = besoinLendemain(suivant, produit);
            if (jauge.compareTo(besoin) < 0) {
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "JAUGE_RELEVEE", "Jauge de " + produit + " de "
                        + Rapport.nombre(jauge) + " L, alors que le lendemain " + Rapport.nombre(besoin) + " L sont vendus "
                        + "sans livraison : stock de clôture relevé à " + Rapport.nombre(besoin) + " L (sinon stock théorique "
                        + "négatif). Écart reporté sur le lendemain ; cumul du mois inchangé.");
                observation = "Jauge du classeur " + Rapport.nombre(jauge) + " L incompatible avec les ventes du lendemain : "
                        + "relevée à " + Rapport.nombre(besoin) + " L (reprise).";
                jauge = besoin;
                relevees.add(produit);
            }
            clotures.add(corps("cuveId", texte(cuve, "cuveId"), "moment", "CLOTURE", "volumeLitres", jauge, "observation", observation));
        }
        gerant.put(base + "/jaugeages", corps("version", version, "jaugeages", clotures));
        return relevees;
    }

    /** Litres vendus le lendemain au-dela des livraisons du lendemain : stock minimal a la cloture du jour. */
    private static BigDecimal besoinLendemain(Jour suivant, String produit) {
        if (suivant == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal livre = suivant.receptions().stream().filter(r -> r.produit().equals(produit))
                .map(Reception::litres).reduce(BigDecimal.ZERO, BigDecimal::add);
        return suivant.attendu().litres().getOrDefault(produit, BigDecimal.ZERO).subtract(livre).max(BigDecimal.ZERO);
    }

    private void credit(Cible c, Jour j, Disposition d, String base) {
        boolean bons = j.bonsDirection().values().stream().anyMatch(v -> v.signum() > 0)
                || (d.toutACredit() ? j.attendu().litres().values().stream().anyMatch(v -> v.signum() > 0)
                : j.bonsGerant().values().stream().anyMatch(v -> v.signum() > 0));
        if (!bons && !(d.toutACredit() && !j.versements().isEmpty())) {
            return;
        }
        JsonNode credit = gerant.get(base + "/credit");
        Map<String, String> produits = new HashMap<>();
        credit.path("produits").forEach(p -> produits.put(texte(p, "code"), texte(p, "id")));
        String jour = j.date().format(AAAAMMJJ);
        for (String produit : d.produits().keySet()) {
            String p = produits.get(produit);
            BigDecimal direction = j.bonsDirection().getOrDefault(produit, BigDecimal.ZERO);
            BigDecimal duGerant = d.toutACredit() ? j.attendu().litres().getOrDefault(produit, BigDecimal.ZERO)
                    : j.bonsGerant().getOrDefault(produit, BigDecimal.ZERO);
            if (direction.signum() > 0) {
                credit = gerant.post(base + "/bons", corps("version", credit.path("version").asInt(), "clientId", client(c, "DIRECTION"),
                        "numero", "R-" + c.code() + "-" + jour + "-D" + produit.substring(0, 2), "produitId", p, "litres", direction,
                        "porteur", "Reprise du classeur"));
            }
            if (duGerant.signum() > 0) {
                credit = gerant.post(base + "/bons", corps("version", credit.path("version").asInt(), "clientId", client(c, "GERANT"),
                        "numero", "R-" + c.code() + "-" + jour + "-G" + produit.substring(0, 2), "produitId", p, "litres", duGerant,
                        "porteur", "Reprise du classeur"));
            }
        }
        if (d.toutACredit()) {
            for (Depot v : j.versements()) {
                try {
                    credit = gerant.post(base + "/recouvrements", corps("version", credit.path("version").asInt(),
                            "clientId", client(c, "GERANT"), "montant", v.montant(),
                            "reference", court(v.reference() == null ? "Versement du classeur" : v.reference(), 80),
                            "observation", "Versement du classeur = recouvrement du client de la station."));
                } catch (Refus e) {
                    if (!"CREANCE_INSUFFISANTE".equals(e.code())) {
                        throw e;
                    }
                    rapport.constater(c.code(), j.date(), j.libelleLignes(), "RECOUVREMENT_REFUSE", e.getMessage());
                }
            }
        }
    }

    private void depenses(Cible c, Jour j, String base) {
        if (j.depenses().signum() <= 0) {
            return;
        }
        int version = gerant.get(base).path("version").asInt();
        JsonNode dep = gerant.post(base + "/depenses", corps("version", version, "natureId", natureAutre(),
                "libelle", "Dépense inscrite au classeur (" + j.libelleLignes() + ")", "beneficiaire", null, "montant", j.depenses()));
        JsonNode derniere = null;
        for (JsonNode x : dep.path("depenses")) {
            derniere = x;
        }
        String depense = texte(Objects.requireNonNull(derniere), "id");
        dep = gerant.fichier(base + "/depenses/" + depense + "/justificatif?version=" + dep.path("version").asInt(),
                "piece-reprise.pdf", "application/pdf", piece(c, j));
        for (JsonNode x : dep.path("depenses")) {
            if (depense.equals(texte(x, "id")) && "A_VALIDER".equals(texte(x, "statut"))) {
                superviseur.post(base + "/depenses/" + depense + "/decision", corps("version", dep.path("version").asInt(),
                        "autoriser", true, "motif", "Dépense reprise du classeur"));
            }
        }
    }

    private void versements(Cible c, Jour j) {
        String jour = j.date().format(AAAAMMJJ);
        int rang = 0;
        for (Depot v : j.versements()) {
            rang++;
            LocalDate date = j.date();
            LocalDate bordereau = v.dateBordereau();
            if (bordereau != null && !bordereau.isBefore(j.date().minusDays(3)) && !bordereau.isAfter(j.date().plusDays(10))
                    && !bordereau.isBefore(o.du())) {
                date = bordereau;
            } else if (bordereau != null) {
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "DATE_BORDEREAU_DOUTEUSE", "Bordereau daté du "
                        + bordereau + " dans « " + v.reference() + " » : versement daté du jour de la ligne.");
            }
            String banque = v.banque() == null ? "NON PRÉCISÉE" : v.banque();
            String reference = v.numero() != null ? "BV " + v.numero() : "R-" + c.code() + "-" + jour + (rang > 1 ? "-" + rang : "");
            String observation = court("Reprise du classeur (" + j.libelleLignes() + ")" + (v.reference() == null ? "" : " : " + v.reference()), 500);
            try {
                verser(c, date, banque, reference, v.montant(), observation);
            } catch (Refus e) {
                if (!"BV_DEJA_SAISI".equals(e.code())) {
                    throw e;
                }
                String autre = court(reference + "-" + jour, 40);
                rapport.constater(c.code(), j.date(), j.libelleLignes(), "BORDEREAU_EN_DOUBLE", "Bordereau " + reference
                        + " déjà saisi : enregistré sous la référence " + autre + ".");
                verser(c, date, banque, autre, v.montant(), observation);
            }
        }
    }

    /**
     * Le classeur nomme les banques par leur abreviation d'usage (BAT, CB...) : celles que l'application ne
     * connait pas encore sont ajoutees telles quelles, et une banque desactivee est reactivee, pour que le
     * versement soit accepte.
     */
    private void assurerBanque(String banque) {
        if (banquesActives == null) {
            banquesActives = new HashSet<>();
            banquesInactives = new HashMap<>();
            for (JsonNode b : admin.get("/api/v1/tresorerie/banques?toutes=true")) {
                if (b.path("actif").asBoolean()) {
                    banquesActives.add(texte(b, "code"));
                } else {
                    banquesInactives.put(texte(b, "code"), b);
                }
            }
        }
        if (banquesActives.contains(banque)) {
            return;
        }
        JsonNode inactive = banquesInactives.remove(banque);
        if (inactive == null) {
            admin.post("/api/v1/tresorerie/banques", corps("code", banque, "nom", banque));
        } else {
            admin.put("/api/v1/tresorerie/banques/" + texte(inactive, "id"), corps("version", inactive.path("version").asInt(), "actif", true));
        }
        banquesActives.add(banque);
    }

    private void verser(Cible c, LocalDate date, String banque, String reference, BigDecimal montant, String observation) {
        assurerBanque(banque);
        // Le bordereau est exige a la creation : le classeur n'en fournit pas, une piece de reprise le dit
        gerant.posterAvecFichier("/api/v1/tresorerie/versements", "versement", corps("stationId", c.uuid(), "dateDepot",
                        date.toString(), "banque", banque, "referenceBv", reference, "montant", montant, "deposant", null,
                        "observation", observation),
                "bordereau-reprise.pdf", "application/pdf", pieceVersement(c, date, banque, reference, montant));
    }

    private byte[] pieceVersement(Cible c, LocalDate date, String banque, String reference, BigDecimal montant) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, o);
        doc.open();
        doc.add(new Paragraph("Pièce de reprise — Jaugeo"));
        doc.add(new Paragraph("Versement de " + Rapport.nombre(montant) + " F (banque " + banque + ", bordereau " + reference
                + ") inscrit au classeur « SUIVI DES STATIONS » pour la station " + c.code() + ", dépôt du " + date + "."));
        doc.add(new Paragraph("Aucun bordereau d'origine n'accompagne le classeur."));
        doc.close();
        return o.toByteArray();
    }

    // ---------------------------------------------------------------- Rapprochement

    private void rapprocherJour(Cible c, Jour j, JsonNode det, String base, Set<String> relevees) {
        Disposition d = c.feuille().disposition();
        List<Comparaison> cs = new ArrayList<>();
        Map<String, BigDecimal> litres = new HashMap<>();
        det.path("totaux").forEach(t -> litres.put(texte(t, "produitCode"), decimal(t, "litres")));
        for (String produit : d.produits().keySet()) {
            cs.add(comparer(c, j, "Litres vendus " + produit, j.attendu().litres().get(produit),
                    litres.getOrDefault(produit, BigDecimal.ZERO)));
        }
        cs.add(comparer(c, j, "Ventes (F)", j.attendu().ventes(), decimal(det, "montant")));
        JsonNode stock = gerant.get(base + "/stock");
        for (JsonNode cuve : stock.path("cuves")) {
            String produit = texte(cuve, "produitCode");
            if (j.jauges().get(produit) != null && !relevees.contains(produit)) {
                BigDecimal attendu = j.attendu().ecartStock().getOrDefault(produit, BigDecimal.ZERO).subtract(baseEcart(c.feuille(), j, produit));
                cs.add(comparer(c, j, "Écart de stock cumulé du mois " + produit + " (L)", attendu, decimal(cuve, "cumulEcartMois")));
            }
        }
        if (!d.toutACredit() && j.attendu().aVerser() != null) {
            JsonNode rec = gerant.get(base + "/tresorerie");
            BigDecimal du = decimal(rec, "ventes").subtract(decimal(rec, "bonsDirection")).subtract(decimal(rec, "bonsGerant"))
                    .subtract(decimal(rec, "complements"));
            cs.add(comparer(c, j, "Montant dû : ventes − bons − compléments (F)", j.attendu().aVerser(), du));
        }
        rapport.journee(c.code(), j.date(), cs);
    }

    private static Comparaison comparer(Cible c, Jour j, String grandeur, BigDecimal classeur, BigDecimal jaugeo) {
        return new Comparaison(c.code(), j.date(), null, grandeur, classeur, jaugeo, j.libelleLignes());
    }

    /** Ecart cumule du classeur a la fin du mois precedent (le classeur cumule depuis le 1er janvier). */
    private static BigDecimal baseEcart(Feuille f, Jour j, String produit) {
        LocalDate debutMois = j.date().withDayOfMonth(1);
        return f.jours().stream().filter(x -> x.date().isBefore(debutMois)).reduce((a, b) -> b)
                .map(x -> x.attendu().ecartStock().getOrDefault(produit, BigDecimal.ZERO)).orElse(BigDecimal.ZERO);
    }

    private void rapprocherMois(YearMonth mois, List<Cible> cibles) {
        JsonNode ventes = comptable.get("/api/v1/syntheses/ventes?mois=" + mois);
        JsonNode stocks = comptable.get("/api/v1/syntheses/stocks?mois=" + mois);
        for (Cible c : cibles) {
            if (rapport.bilan(c.code()).journees() == 0) {
                continue;
            }
            JsonNode ligne = null;
            for (JsonNode l : ventes.path("lignes")) {
                if (c.code().equals(texte(l, "stationCode"))) {
                    ligne = l;
                }
            }
            Optional<LigneAnalyse> a = analyse(c.code(), mois);
            List<Jour> jours = c.feuille().jours().stream().filter(j -> YearMonth.from(j.date()).equals(mois)).toList();
            if (ligne != null && a.isPresent()) {
                LigneAnalyse l = a.get();
                for (String produit : c.feuille().disposition().produits().keySet()) {
                    BigDecimal feuille = jours.stream().map(j -> j.attendu().litres().getOrDefault(produit, BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    mensuelle(c, mois, "Litres vendus " + produit, l.litres().get(produit), decimal(ligne.path("litres"), produit),
                            "Feuille station : " + Rapport.nombre(feuille) + " L.");
                }
                BigDecimal recettes = jours.stream().map(j -> Objects.requireNonNullElse(j.attendu().ventes(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                mensuelle(c, mois, "Recettes du mois (E)", l.recettes(), decimal(ligne, "recettes"),
                        "Feuille station : " + Rapport.nombre(recettes) + " F.");
                mensuelle(c, mois, "Solde de caisse d'ouverture (F)", l.soldeOuverture(), decimal(ligne, "soldeOuverture"),
                        "Le solde d'ouverture de Jaugeo est le solde de fin du mois précédent calculé jour par jour.");
                mensuelle(c, mois, "Créances au début du mois (G)", l.creancesDebut(), decimal(ligne, "creancesDebut"),
                        "Créances de fin du mois précédent dans Jaugeo.");
                BigDecimal verse = jours.stream().flatMap(j -> j.versements().stream()).map(Depot::montant)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                mensuelle(c, mois, "Versements bancaires (I)", l.versements(), decimal(ligne, "versements"),
                        "Feuille station : " + Rapport.nombre(verse) + " F inscrits sur les lignes du mois. Jaugeo range "
                                + "chaque versement au mois de la date du bordereau ; l'analyse peut compter des dépôts absents des feuilles.");
                mensuelle(c, mois, "Solde de caisse du gérant (J)", l.soldeCaisse(), decimal(ligne, "soldeCaisse"),
                        "Suit les versements (date des bordereaux) et le solde d'ouverture.");
                mensuelle(c, mois, "Créances en fin de mois (K)", l.creancesFin(), decimal(ligne, "creancesFin"),
                        "Créances d'ouverture + bons − recouvrements enregistrés. Les bons de la direction restent une "
                                + "créance dans Jaugeo ; l'analyse les range parfois en « Restant BL » (colonne O).");
                mensuelle(c, mois, "Dépenses autorisées (L)", l.depenses(), decimal(ligne, "depenses"),
                        "L'analyse ne reprend pas toujours les dépenses des feuilles station.");
                mensuelle(c, mois, "Compléments citerne (M)", l.complements(), decimal(ligne, "complements"),
                        "Compléments non repris : voir les particularités du classeur.");
                mensuelle(c, mois, "Écart sur versement (R)", l.ecart(), decimal(ligne, "ecart"),
                        "Conséquence des différences ci-dessus.");
            } else if (a.isEmpty()) {
                rapport.constater(c.code(), mois.atDay(1), null, "ANALYSE_ABSENTE", "Pas de ligne pour la station dans la "
                        + "feuille d'analyse de " + mois + " : rapprochement mensuel impossible.");
            }
            for (JsonNode s : stocks.path("lignes")) {
                if (!c.code().equals(texte(s, "stationCode")) || jours.isEmpty()) {
                    continue;
                }
                String produit = texte(s, "produitCode");
                Jour dernier = jours.get(jours.size() - 1);
                BigDecimal attendu = dernier.attendu().ecartStock().getOrDefault(produit, BigDecimal.ZERO)
                        .subtract(baseEcart(c.feuille(), dernier, produit));
                mensuelle(c, mois, "Écart de stock du mois " + produit + " (L)", attendu, decimal(s, "ecartLitres"),
                        "Le classeur cumule l'écart depuis le 1er janvier ; une jauge absente y crée un écart fictif.");
            }
        }
    }

    private void mensuelle(Cible c, YearMonth mois, String grandeur, BigDecimal analyse, BigDecimal jaugeo, String explication) {
        rapport.mensuelle(new Comparaison(c.code(), null, mois, grandeur, analyse == null ? BigDecimal.ZERO : analyse,
                jaugeo == null ? BigDecimal.ZERO : jaugeo, explication));
    }

    // ---------------------------------------------------------------- Outils

    private Optional<LigneAnalyse> analyse(String station, YearMonth mois) {
        return classeur.analyses().stream().filter(a -> a.station().equals(station) && a.mois().equals(mois)).findFirst();
    }

    /** Client de reprise de la station : bons de la direction, ou du gerant (Hydrobase : son client unique). */
    private String client(Cible c, String recouvrement) {
        String code = "REP-" + c.code() + ("DIRECTION".equals(recouvrement) ? "-DIR" : "-GER");
        return clients.computeIfAbsent(code, k -> {
            for (JsonNode x : comptable.get("/api/v1/clients")) {
                if (k.equals(texte(x, "code"))) {
                    return texte(x, "id");
                }
            }
            String nom = "DIRECTION".equals(recouvrement) ? "Clients en compte " + c.code() + " (reprise)"
                    : o.clientsCredit().getOrDefault(c.code(), "Créances antérieures " + c.code() + " (reprise)");
            return texte(comptable.post("/api/v1/clients", corps("code", k, "nom", nom, "recouvrement", recouvrement,
                    "actif", true, "observation", "Créé par la reprise du classeur.")), "id");
        });
    }

    private String natureAutre() {
        if (natureAutre == null) {
            for (JsonNode n : gerant.get("/api/v1/depenses/natures")) {
                if ("AUTRE".equals(texte(n, "code"))) {
                    natureAutre = texte(n, "id");
                }
            }
            exiger(natureAutre, "Nature de dépense AUTRE absente.");
        }
        return natureAutre;
    }

    /** Piece justificative generee : la depense provient du classeur, sans piece d'origine. */
    private byte[] piece(Cible c, Jour j) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, o);
        doc.open();
        doc.add(new Paragraph("Pièce de reprise — Jaugeo"));
        doc.add(new Paragraph("Dépense de " + Rapport.nombre(j.depenses()) + " F inscrite au classeur « SUIVI DES STATIONS » "
                + "pour la station " + c.code() + ", journée du " + j.date() + " (" + j.libelleLignes() + ")."));
        doc.add(new Paragraph("Aucune pièce d'origine n'accompagne le classeur."));
        doc.close();
        return o.toByteArray();
    }

    private static String exiger(String v, String message) {
        if (v == null) {
            throw new Arret(message);
        }
        return v;
    }

    private static boolean positif(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    static String court(String t, int max) {
        return t == null || t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

}
