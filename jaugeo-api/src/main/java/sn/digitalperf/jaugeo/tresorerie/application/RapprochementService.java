package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation;
import sn.digitalperf.jaugeo.referentiel.ReferentielConsultation.StationRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.domaine.EncaissementJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.EncaissementJourneeDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleve;
import sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleve.Nature;
import sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleveDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.OperationReleve;
import sn.digitalperf.jaugeo.tresorerie.domaine.OperationReleveDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.Releve;
import sn.digitalperf.jaugeo.tresorerie.domaine.ReleveDepot;
import sn.digitalperf.jaugeo.tresorerie.domaine.Versement;
import sn.digitalperf.jaugeo.tresorerie.domaine.VersementDepot;

/**
 * Rapprochement (M5) : import des releves bancaires et marchands selon un format parametre par banque ou operateur,
 * lettrage des credits bancaires avec les versements (BV), comparaison des releves Wave, Orange Money et carte
 * aux encaissements declares par les stations. Reserve aux utilisateurs de perimetre reseau.
 */
@Service
@Transactional(readOnly = true)
public class RapprochementService {

    /** Le BV est depose au plus 7 jours avant son credit au releve, au plus 1 jour apres (date de valeur). */
    static final int JOURS_AVANT = 7;
    static final int JOURS_APRES = 1;
    static final int TAILLE_MAX = 5 * 1024 * 1024;
    static final Set<String> SEPARATEURS = Set.of(";", ",", "\t", "|");

    public record FormatVue(UUID id, String code, String libelle, String nature, String separateur, String formatDate,
                            int lignesIgnorees, int colDate, int colLibelle, Integer colReference, int colCredit,
                            boolean actif, Integer version) {
    }

    public record BilanImport(UUID releveId, String nomFichier, String format, LocalDate dateMin, LocalDate dateMax,
                              int operations, int doublons, int ignorees, int lettrees) {
    }

    public record ReleveVue(UUID id, String nomFichier, String format, String nature, String stationCode,
                            LocalDate dateMin, LocalDate dateMax, int operations, int doublons, String importePar,
                            java.time.Instant importeLe) {
    }

    public record VersementResume(UUID id, String stationCode, LocalDate dateDepot, String banque, String referenceBv,
                                  BigDecimal montant, String statut) {
    }

    public record OperationVue(UUID id, LocalDate date, String libelle, String reference, BigDecimal montant,
                               String format, VersementResume versement, String modeLettrage, String lettrePar,
                               List<VersementResume> candidats) {
    }

    public record Rapprochement(String mois, int versements, BigDecimal montantVersements, int versementsLettres,
                                BigDecimal montantLettre, BigDecimal taux, List<OperationVue> operations,
                                List<VersementResume> versementsNonLettres, List<ReleveVue> releves,
                                boolean peutLettrer) {
    }

    public record EcartElectronique(UUID stationId, String stationCode, LocalDate date, String mode, String libelle,
                                    BigDecimal declare, BigDecimal releve, BigDecimal ecart) {
    }

    public record RapprochementElectronique(String mois, List<EcartElectronique> lignes, BigDecimal totalDeclare,
                                            BigDecimal totalReleve, int ecarts, int nonCouverts) {
    }

    private final FormatReleveDepot formats;
    private final ReleveDepot releves;
    private final OperationReleveDepot operations;
    private final VersementDepot versements;
    private final EncaissementJourneeDepot encaissements;
    private final ReferentielConsultation referentiel;
    private final PerimetreCourant perimetre;
    private final Clock horloge;

    public RapprochementService(FormatReleveDepot formats, ReleveDepot releves, OperationReleveDepot operations,
                                VersementDepot versements, EncaissementJourneeDepot encaissements,
                                ReferentielConsultation referentiel, PerimetreCourant perimetre, Clock horloge) {
        this.formats = formats;
        this.releves = releves;
        this.operations = operations;
        this.versements = versements;
        this.encaissements = encaissements;
        this.referentiel = referentiel;
        this.perimetre = perimetre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Formats

    public List<FormatVue> formats() {
        exigerReseau();
        return formats.findAllByOrderByCodeAsc().stream().map(RapprochementService::vue).toList();
    }

    @Transactional
    public FormatVue enregistrerFormat(UUID id, Integer version, FormatReleve.Parametres p) {
        exigerReseau();
        if (!SEPARATEURS.contains(p.separateur())) {
            throw ErreurMetier.regle("SEPARATEUR_INVALIDE", "Séparateur accepté : point-virgule, virgule, tabulation ou barre verticale.");
        }
        try {
            DateTimeFormatter.ofPattern(p.formatDate().trim());
        } catch (IllegalArgumentException e) {
            throw ErreurMetier.regle("FORMAT_DATE_INVALIDE", "Format de date invalide (exemples : dd/MM/yyyy, yyyy-MM-dd).");
        }
        String code = p.code().trim().toUpperCase();
        formats.findByCode(code).filter(autre -> !autre.getUuid().equals(id)).ifPresent(autre -> {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Un format de relevé porte déjà le code " + code + ".");
        });
        FormatReleve f;
        if (id == null) {
            f = new FormatReleve(p);
        } else {
            f = formats.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Format de relevé"));
            f.verifierVersion(version);
            f.parametrer(p);
        }
        return vue(formats.saveAndFlush(f));
    }

    // ---------------------------------------------------------------- Import

    @Transactional
    public BilanImport importer(UUID formatId, UUID stationId, String nomFichier, byte[] contenu) {
        exigerReseau();
        FormatReleve f = formats.findByUuid(formatId).orElseThrow(() -> ErreurMetier.introuvable("Format de relevé"));
        if (!f.isActif()) {
            throw ErreurMetier.regle("FORMAT_INACTIF", "Ce format de relevé n'est plus utilisé.");
        }
        StationRef station = null;
        if (f.getNature() != Nature.BANQUE) {
            if (stationId == null) {
                throw ErreurMetier.regle("STATION_OBLIGATOIRE", "Indiquez la station du compte marchand " + f.getLibelle() + ".");
            }
            station = referentiel.station(stationId).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        }
        if (contenu == null || contenu.length == 0) {
            throw ErreurMetier.regle("FICHIER_VIDE", "Joignez le relevé (CSV ou Excel).");
        }
        if (contenu.length > TAILLE_MAX) {
            throw ErreurMetier.regle("FICHIER_TROP_LOURD", "Le relevé dépasse 5 Mo : découpez-le par période.");
        }
        String empreinte = empreinte(contenu);
        releves.findByEmpreinte(empreinte).ifPresent(r -> {
            throw ErreurMetier.conflit("RELEVE_DEJA_IMPORTE", "Ce fichier a déjà été importé (" + r.getNomFichier() + ").");
        });
        LecteurReleve.Lecture lecture = LecteurReleve.lire(contenu, nomFichier, f);
        if (lecture.operations().isEmpty()) {
            throw ErreurMetier.regle("RELEVE_VIDE", "Aucune opération au crédit trouvée : vérifiez les colonnes du format "
                    + f.getCode() + ".");
        }
        String nom = nomFichier == null || nomFichier.isBlank() ? "releve" : nomFichier.strip();
        Releve r = releves.save(new Releve(f.getId(), station == null ? null : station.id(),
                nom.length() > 200 ? nom.substring(0, 200) : nom, empreinte));
        Map<String, Integer> occurrences = new HashMap<>();
        int doublons = 0;
        int importees = 0;
        LocalDate min = null;
        LocalDate max = null;
        for (LecteurReleve.Operation o : lecture.operations()) {
            String base = String.join("|", String.valueOf(station == null ? "" : station.id()), o.date().toString(),
                    Objects.toString(o.reference(), ""), o.montant().toPlainString(), o.libelle().toUpperCase());
            int rang = occurrences.merge(base, 1, Integer::sum);
            String cle = empreinte((base + "|" + rang).getBytes(StandardCharsets.UTF_8));
            min = min == null || o.date().isBefore(min) ? o.date() : min;
            max = max == null || o.date().isAfter(max) ? o.date() : max;
            if (operations.existsByFormatIdAndCle(f.getId(), cle)) {
                doublons++;
                continue;
            }
            operations.save(new OperationReleve(r.getId(), f.getId(), station == null ? null : station.id(), o.date(),
                    o.libelle(), o.reference(), o.montant(), cle));
            importees++;
        }
        r.bilan(min, max, importees, doublons);
        releves.save(r);
        operations.flush();
        int lettrees = f.getNature() == Nature.BANQUE ? lettrerAutomatiquement() : 0;
        return new BilanImport(r.getUuid(), r.getNomFichier(), f.getLibelle(), min, max, importees, doublons,
                lecture.ignorees(), lettrees);
    }

    // ---------------------------------------------------------------- Lettrage

    /**
     * Lettre chaque credit bancaire non lettre avec le versement non lettre de meme montant depose dans la fenetre :
     * celui dont la reference de BV figure dans le libelle ou la reference de l'operation, sinon le seul candidat
     * si aucun autre credit ne peut y pretendre. Les cas ambigus restent au lettrage manuel.
     */
    @Transactional
    public int lettrerAutomatiquement() {
        exigerReseau();
        List<OperationReleve> ouvertes = operations.bancairesNonLettrees();
        Set<Long> pris = new HashSet<>();
        Set<Long> lettrees = new HashSet<>();
        int n = 0;
        for (OperationReleve o : ouvertes) {
            List<Versement> candidats = versements.candidatsLettrage(o.getMontant(), o.getDateOperation().minusDays(JOURS_AVANT),
                    o.getDateOperation().plusDays(JOURS_APRES)).stream().filter(v -> !pris.contains(v.getId())).toList();
            if (candidats.isEmpty()) {
                continue;
            }
            String texte = normaliser(o.getLibelle() + " " + Objects.toString(o.getReference(), ""));
            List<Versement> parReference = candidats.stream().filter(v -> {
                String bv = normaliser(v.getReferenceBv());
                return bv.length() >= 4 && texte.contains(bv);
            }).toList();
            Versement choisi = null;
            if (parReference.size() == 1) {
                choisi = parReference.get(0);
            } else if (parReference.isEmpty() && candidats.size() == 1) {
                Versement v = candidats.get(0);
                long concurrents = ouvertes.stream().filter(x -> !lettrees.contains(x.getId())
                        && x.getMontant().compareTo(v.getMontant()) == 0
                        && !x.getDateOperation().isBefore(v.getDateDepot().minusDays(JOURS_APRES))
                        && !x.getDateOperation().isAfter(v.getDateDepot().plusDays(JOURS_AVANT))).count();
                choisi = concurrents == 1 ? v : null;
            }
            if (choisi != null) {
                o.lettrer(choisi, OperationReleve.Lettrage.AUTO, "lettrage automatique", horloge.instant());
                pris.add(choisi.getId());
                lettrees.add(o.getId());
                n++;
            }
        }
        operations.flush();
        return n;
    }

    @Transactional
    public OperationVue lettrer(UUID operationId, UUID versementId) {
        exigerReseau();
        OperationReleve o = operationBancaire(operationId);
        Versement v = versements.findByUuid(versementId).orElseThrow(() -> ErreurMetier.introuvable("Versement"));
        if (!operations.findByVersementIdIn(List.of(v.getId())).isEmpty()) {
            throw ErreurMetier.conflit("VERSEMENT_DEJA_LETTRE", "Ce versement est déjà rapproché d'une autre opération du relevé.");
        }
        o.lettrer(v, OperationReleve.Lettrage.MANUEL, perimetre.identifiant(), horloge.instant());
        operations.saveAndFlush(o);
        return vues(List.of(o), true).get(0);
    }

    @Transactional
    public OperationVue delettrer(UUID operationId) {
        exigerReseau();
        OperationReleve o = operationBancaire(operationId);
        o.delettrer();
        operations.saveAndFlush(o);
        return vues(List.of(o), true).get(0);
    }

    private OperationReleve operationBancaire(UUID id) {
        OperationReleve o = operations.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Opération du relevé"));
        if (formats.findById(o.getFormatId()).map(FormatReleve::getNature).orElse(null) != Nature.BANQUE) {
            throw ErreurMetier.regle("RELEVE_MARCHAND", "Seules les opérations d'un relevé bancaire se rapprochent d'un versement.");
        }
        return o;
    }

    // ---------------------------------------------------------------- Vues

    /** Credits bancaires du mois et leur lettrage ; versements deposes dans le mois et pas encore rapproches. */
    public Rapprochement rapprochement(YearMonth mois) {
        exigerReseau();
        LocalDate du = mois.atDay(1);
        LocalDate au = mois.atEndOfMonth();
        List<OperationReleve> ops = operations.parNature(Nature.BANQUE, du, au);
        List<Versement> deposes = versements.deposesEntre(du, au);
        Set<Long> lettres = operations.findByVersementIdIn(deposes.stream().map(Versement::getId).toList()).stream()
                .map(OperationReleve::getVersementId).collect(Collectors.toSet());
        Map<Long, StationRef> stations = stations();
        List<VersementResume> nonLettres = deposes.stream().filter(v -> !lettres.contains(v.getId()))
                .map(v -> resume(v, stations)).toList();
        BigDecimal total = deposes.stream().map(Versement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lettre = deposes.stream().filter(v -> lettres.contains(v.getId())).map(Versement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taux = total.signum() == 0 ? null : lettre.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        Map<Long, FormatReleve> parFormat = formats.findAll().stream().collect(Collectors.toMap(FormatReleve::getId, Function.identity()));
        List<ReleveVue> vuesReleves = releves.couvrant(du, au).stream().map(r -> {
            FormatReleve f = parFormat.get(r.getFormatId());
            StationRef s = r.getStationId() == null ? null : stations.get(r.getStationId());
            return new ReleveVue(r.getUuid(), r.getNomFichier(), f.getLibelle(), f.getNature().name(), s == null ? null : s.code(),
                    r.getDateMin(), r.getDateMax(), r.getOperations(), r.getDoublons(), r.getCreePar(), r.getCreeLe());
        }).toList();
        return new Rapprochement(mois.toString(), deposes.size(), total, lettres.size(), lettre, taux, vues(ops, false),
                nonLettres, vuesReleves, aPermission("VERSEMENT_VALIDER"));
    }

    /** Releves Wave, Orange Money et carte compares, jour par jour, aux encaissements declares par les stations. */
    public RapprochementElectronique electronique(YearMonth mois) {
        exigerReseau();
        LocalDate du = mois.atDay(1);
        LocalDate au = mois.atEndOfMonth();
        Map<Long, StationRef> stations = stations();
        record Cle(long stationId, LocalDate date, EncaissementJournee.Mode mode) {
        }
        Map<Cle, BigDecimal> declares = new HashMap<>();
        encaissements.findByDateJourBetween(du, au).forEach(e ->
                declares.merge(new Cle(e.getStationId(), e.getDateJour(), e.getMode()), e.getMontant(), BigDecimal::add));
        Map<Cle, BigDecimal> releve = new HashMap<>();
        for (EncaissementJournee.Mode m : EncaissementJournee.Mode.values()) {
            operations.parNature(Nature.valueOf(m.name()), du, au).stream().filter(o -> o.getStationId() != null).forEach(o ->
                    releve.merge(new Cle(o.getStationId(), o.getDateOperation(), m), o.getMontant(), BigDecimal::add));
        }
        // Periodes couvertes par les releves marchands importes, par station et mode
        Map<Long, FormatReleve> parFormat = formats.findAll().stream().collect(Collectors.toMap(FormatReleve::getId, Function.identity()));
        List<Releve> couvrants = releves.couvrant(du, au).stream()
                .filter(r -> r.getStationId() != null && parFormat.get(r.getFormatId()).getNature() != Nature.BANQUE).toList();
        Set<Cle> cles = new HashSet<>(declares.keySet());
        cles.addAll(releve.keySet());
        List<EcartElectronique> lignes = new ArrayList<>();
        BigDecimal totalDeclare = BigDecimal.ZERO;
        BigDecimal totalReleve = BigDecimal.ZERO;
        int ecarts = 0;
        int nonCouverts = 0;
        for (Cle c : cles.stream().sorted(Comparator.comparing(Cle::date).thenComparing(c -> stations.containsKey(c.stationId())
                ? stations.get(c.stationId()).code() : "").thenComparing(Cle::mode)).toList()) {
            StationRef s = stations.get(c.stationId());
            if (s == null) {
                continue;
            }
            boolean couvert = couvrants.stream().anyMatch(r -> r.getStationId() == c.stationId()
                    && parFormat.get(r.getFormatId()).getNature().name().equals(c.mode().name())
                    && !c.date().isBefore(r.getDateMin()) && !c.date().isAfter(r.getDateMax()));
            BigDecimal d = declares.getOrDefault(c, BigDecimal.ZERO);
            BigDecimal r = couvert ? releve.getOrDefault(c, BigDecimal.ZERO) : null;
            BigDecimal ecart = r == null ? null : r.subtract(d);
            if (ecart != null && ecart.signum() != 0) {
                ecarts++;
            }
            if (r == null) {
                nonCouverts++;
            } else {
                totalReleve = totalReleve.add(r);
            }
            totalDeclare = totalDeclare.add(d);
            lignes.add(new EcartElectronique(s.uuid(), s.code(), c.date(), c.mode().name(), EncaissementService.LIBELLES.get(c.mode()),
                    d, r, ecart));
        }
        return new RapprochementElectronique(mois.toString(), lignes, totalDeclare, totalReleve, ecarts, nonCouverts);
    }

    private List<OperationVue> vues(List<OperationReleve> ops, boolean sansCandidats) {
        Map<Long, StationRef> stations = stations();
        Map<Long, FormatReleve> parFormat = formats.findAll().stream().collect(Collectors.toMap(FormatReleve::getId, Function.identity()));
        Map<Long, Versement> lies = versements.findAllById(ops.stream().map(OperationReleve::getVersementId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Versement::getId, Function.identity()));
        Map<String, List<Versement>> cache = new TreeMap<>();
        return ops.stream().map(o -> {
            Versement v = o.getVersementId() == null ? null : lies.get(o.getVersementId());
            List<VersementResume> candidats = List.of();
            if (v == null && !sansCandidats) {
                String cle = o.getMontant().toPlainString() + "|" + o.getDateOperation();
                candidats = cache.computeIfAbsent(cle, k -> versements.candidatsLettrage(o.getMontant(),
                                o.getDateOperation().minusDays(30), o.getDateOperation().plusDays(JOURS_APRES)))
                        .stream().map(x -> resume(x, stations)).toList();
            }
            return new OperationVue(o.getUuid(), o.getDateOperation(), o.getLibelle(), o.getReference(), o.getMontant(),
                    parFormat.get(o.getFormatId()).getLibelle(), v == null ? null : resume(v, stations),
                    o.getModeLettrage() == null ? null : o.getModeLettrage().name(), o.getLettrePar(), candidats);
        }).toList();
    }

    private static VersementResume resume(Versement v, Map<Long, StationRef> stations) {
        StationRef s = stations.get(v.getStationId());
        return new VersementResume(v.getUuid(), s == null ? null : s.code(), v.getDateDepot(), v.getBanque(), v.getReferenceBv(),
                v.getMontant(), v.getStatut().name());
    }

    private static FormatVue vue(FormatReleve f) {
        return new FormatVue(f.getUuid(), f.getCode(), f.getLibelle(), f.getNature().name(), f.getSeparateur(), f.getFormatDate(),
                f.getLignesIgnorees(), f.getColDate(), f.getColLibelle(), f.getColReference(), f.getColCredit(), f.isActif(),
                f.getVersion());
    }

    private Map<Long, StationRef> stations() {
        return referentiel.stations().stream().collect(Collectors.toMap(StationRef::id, Function.identity()));
    }

    private void exigerReseau() {
        if (!perimetre.estReseau()) {
            throw ErreurMetier.interdit("Le rapprochement bancaire est réservé aux services du siège.");
        }
    }

    static String normaliser(String s) {
        return s == null ? "" : s.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean aPermission(String permission) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(permission::equals);
    }
}
