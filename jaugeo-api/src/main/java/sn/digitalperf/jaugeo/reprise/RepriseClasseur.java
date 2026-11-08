package sn.digitalperf.jaugeo.reprise;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import sn.digitalperf.jaugeo.reprise.LecteurClasseur.Affectation;

/**
 * Outil de reprise du classeur « SUIVI DES STATIONS » (ligne de commande). Lit le classeur, rejoue chaque journee
 * de la periode dans Jaugeo par l'API, puis ecrit le rapport de rapprochement.
 *
 * <pre>
 * JAUGEO_MDP_ADMIN=... java -cp jaugeo-api.jar -Dloader.main=sn.digitalperf.jaugeo.reprise.RepriseClasseur \
 *     org.springframework.boot.loader.launch.PropertiesLauncher \
 *     --classeur "SUIVI DES STATIONS 2026.xlsx" --du 2026-01-01 --au 2026-02-28 [--api http://localhost:8080]
 *     [--stations NIA,HYD] [--admin admin] [--rapport rapport-reprise.md] [--simulation]
 *     [--feuille "STATION TEST=TST:STANDARD"] [--client HYD=Cheikh Omar]
 * </pre>
 * Le mot de passe de l'administrateur est lu dans la variable d'environnement JAUGEO_MDP_ADMIN (jamais en argument).
 */
public final class RepriseClasseur {

    /** Parametres de la reprise. */
    public record Options(Path classeur, String api, LocalDate du, LocalDate au, Set<String> stations, String admin,
                          String motDePasseAdmin, Path rapport, boolean simulation, Map<String, Affectation> feuilles,
                          Map<String, String> alias, Map<String, String> clientsCredit) {
    }

    private RepriseClasseur() {
    }

    public static void main(String[] args) {
        try {
            Options o = options(args, System.getenv("JAUGEO_MDP_ADMIN"));
            Rapport r = executer(o, System.out);
            System.exit(r.conforme() ? 0 : 2);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage : --classeur FICHIER.xlsx --du AAAA-MM-JJ --au AAAA-MM-JJ [--api URL] [--stations NIA,HYD]"
                    + " [--admin IDENTIFIANT] [--rapport FICHIER.md] [--simulation] ; mot de passe dans JAUGEO_MDP_ADMIN.");
            System.exit(1);
        } catch (IOException | ApiJaugeo.Refus e) {
            System.err.println("Reprise interrompue : " + e.getMessage());
            System.exit(1);
        }
    }

    /** Lit le classeur, rejoue la periode (sauf simulation) et ecrit le rapport. */
    public static Rapport executer(Options o, PrintStream journal) throws IOException {
        Classeur classeur;
        try (InputStream in = Files.newInputStream(o.classeur())) {
            classeur = new LecteurClasseur(o.feuilles(), o.alias()).lire(in);
        }
        journal.println("Classeur lu : " + classeur.feuilles().size() + " feuilles station, "
                + classeur.analyses().size() + " lignes d'analyse, " + classeur.constats().size() + " particularités.");
        Rapport rapport = new Rapport(o.du(), o.au(), o.simulation(), classeur.constats());
        if (!o.simulation()) {
            new ExecutionReprise(new ApiJaugeo(o.api()), classeur, o, rapport, journal).lancer();
        }
        Files.writeString(o.rapport(), rapport.markdown(o.classeur().getFileName().toString()), StandardCharsets.UTF_8);
        journal.println("Rapport : " + o.rapport().toAbsolutePath());
        return rapport;
    }

    static Options options(String[] args, String motDePasse) {
        Map<String, String> v = new LinkedHashMap<>();
        Map<String, Affectation> feuilles = LecteurClasseur.feuillesParDefaut();
        Map<String, String> alias = new LinkedHashMap<>(LecteurClasseur.aliasParDefaut());
        Map<String, String> clients = new LinkedHashMap<>(Map.of("HYD", "Cheikh Omar"));
        boolean simulation = false;
        List<String> a = List.of(args);
        for (int i = 0; i < a.size(); i++) {
            String cle = a.get(i);
            if ("--simulation".equals(cle)) {
                simulation = true;
                continue;
            }
            if (!cle.startsWith("--") || i + 1 >= a.size()) {
                throw new IllegalArgumentException("Argument inattendu : " + cle);
            }
            String valeur = a.get(++i);
            switch (cle) {
                case "--feuille" -> {
                    // "NOM DE LA FEUILLE=CODE:DISPOSITION"
                    String[] p = valeur.split("=", 2);
                    String[] q = p.length == 2 ? p[1].split(":", 2) : new String[0];
                    if (q.length != 2) {
                        throw new IllegalArgumentException("--feuille attend « NOM=CODE:DISPOSITION » : " + valeur);
                    }
                    feuilles.put(LecteurClasseur.normaliser(p[0]), new Affectation(q[0].trim().toUpperCase(Locale.ROOT),
                            Disposition.parType(q[1].trim())));
                }
                case "--alias" -> {
                    String[] p = valeur.split("=", 2);
                    alias.put(LecteurClasseur.normaliser(p[0]), p[1].trim().toUpperCase(Locale.ROOT));
                }
                case "--client" -> {
                    String[] p = valeur.split("=", 2);
                    clients.put(p[0].trim().toUpperCase(Locale.ROOT), p[1].trim());
                }
                default -> v.put(cle, valeur);
            }
        }
        if (!v.containsKey("--classeur")) {
            throw new IllegalArgumentException("Indiquez le classeur (--classeur).");
        }
        Path classeur = Path.of(v.get("--classeur"));
        if (!Files.isReadable(classeur)) {
            throw new IllegalArgumentException("Classeur introuvable : " + classeur.toAbsolutePath());
        }
        LocalDate du = LecteurClasseur.dateIso(v.getOrDefault("--du", "2026-01-01"));
        LocalDate au = LecteurClasseur.dateIso(v.getOrDefault("--au", "2026-02-28"));
        if (au.isBefore(du)) {
            throw new IllegalArgumentException("La période est vide : --au précède --du.");
        }
        if (!simulation && (motDePasse == null || motDePasse.isBlank())) {
            throw new IllegalArgumentException("Mot de passe de l'administrateur absent : renseignez la variable JAUGEO_MDP_ADMIN.");
        }
        Set<String> stations = new LinkedHashSet<>();
        for (String s : v.getOrDefault("--stations", "").split(",")) {
            if (!s.isBlank()) {
                stations.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        List<String> inconnues = new ArrayList<>(v.keySet());
        inconnues.removeAll(List.of("--classeur", "--du", "--au", "--api", "--stations", "--admin", "--rapport"));
        if (!inconnues.isEmpty()) {
            throw new IllegalArgumentException("Option inconnue : " + String.join(", ", inconnues));
        }
        return new Options(classeur, v.getOrDefault("--api", "http://localhost:8080"), du, au, stations,
                v.getOrDefault("--admin", "admin"), motDePasse, Path.of(v.getOrDefault("--rapport", "rapport-reprise.md")),
                simulation, feuilles, alias, clients);
    }
}
