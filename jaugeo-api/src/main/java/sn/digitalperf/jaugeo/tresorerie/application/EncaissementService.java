package sn.digitalperf.jaugeo.tresorerie.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation;
import sn.digitalperf.jaugeo.exploitation.JourneesExploitation.JourneeRef;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;
import sn.digitalperf.jaugeo.tresorerie.domaine.EncaissementJournee;
import sn.digitalperf.jaugeo.tresorerie.domaine.EncaissementJournee.Mode;
import sn.digitalperf.jaugeo.tresorerie.domaine.EncaissementJourneeDepot;

/**
 * Encaissements electroniques de la journee (M5) : le gerant saisit le total encaisse par Wave, Orange Money et
 * carte ; ces montants sortent des especes a verser et se rapprochent ensuite des releves marchands et bancaires.
 */
@Service
@Transactional(readOnly = true)
public class EncaissementService {

    public static final Map<Mode, String> LIBELLES = Map.of(Mode.WAVE, "Wave", Mode.ORANGE_MONEY, "Orange Money",
            Mode.CARTE, "Carte bancaire");

    public record LigneEncaissement(Mode mode, String libelle, BigDecimal montant) {
    }

    public record EncaissementsJournee(UUID journeeId, Integer version, String statut, boolean modifiable,
                                       List<LigneEncaissement> lignes, BigDecimal total) {
    }

    public record SaisieEncaissement(Mode mode, BigDecimal montant) {
    }

    private final JourneesExploitation exploitation;
    private final PerimetreCourant perimetre;
    private final EncaissementJourneeDepot encaissements;

    public EncaissementService(JourneesExploitation exploitation, PerimetreCourant perimetre,
                               EncaissementJourneeDepot encaissements) {
        this.exploitation = exploitation;
        this.perimetre = perimetre;
        this.encaissements = encaissements;
    }

    public EncaissementsJournee journee(UUID journeeUuid) {
        JourneeRef j = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j.stationId());
        return vue(j);
    }

    /** Remplace les totaux des modes transmis ; un montant nul ou absent retire le mode. */
    @Transactional
    public EncaissementsJournee enregistrer(UUID journeeUuid, Integer version, List<SaisieEncaissement> saisies) {
        JourneeRef j0 = exploitation.journee(journeeUuid).orElseThrow(() -> ErreurMetier.introuvable("Journée"));
        perimetre.exigerStation(j0.stationId());
        JourneeRef j = exploitation.verrouillerPourSaisie(journeeUuid, version);
        Map<Mode, EncaissementJournee> existants = new EnumMap<>(Mode.class);
        encaissements.findByJourneeId(j.id()).forEach(e -> existants.put(e.getMode(), e));
        for (SaisieEncaissement s : saisies) {
            if (s.mode() == null) {
                throw ErreurMetier.regle("MODE_OBLIGATOIRE", "Indiquez le mode d'encaissement : Wave, Orange Money ou carte.");
            }
            EncaissementJournee e = existants.get(s.mode());
            if (s.montant() == null || s.montant().signum() == 0) {
                if (e != null) {
                    encaissements.delete(e);
                }
                continue;
            }
            if (s.montant().signum() < 0 || s.montant().stripTrailingZeros().scale() > 0) {
                throw ErreurMetier.regle("MONTANT_INVALIDE", LIBELLES.get(s.mode()) + " : le montant est un nombre entier de francs.");
            }
            if (e == null) {
                e = new EncaissementJournee(j.id(), j.stationId(), j.date(), s.mode());
            }
            e.encaisser(s.montant());
            encaissements.save(e);
        }
        encaissements.flush();
        return vue(j);
    }

    private EncaissementsJournee vue(JourneeRef j) {
        Map<Mode, BigDecimal> montants = new EnumMap<>(Mode.class);
        encaissements.findByJourneeId(j.id()).forEach(e -> montants.put(e.getMode(), e.getMontant()));
        List<LigneEncaissement> lignes = new ArrayList<>();
        for (Mode m : Mode.values()) {
            lignes.add(new LigneEncaissement(m, LIBELLES.get(m), montants.get(m)));
        }
        BigDecimal total = montants.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EncaissementsJournee(j.uuid(), j.version(), j.statut(), j.brouillon() && aPermission("JOURNEE_SAISIR"),
                lignes, total);
    }

    private static boolean aPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(permission::equals);
    }
}
