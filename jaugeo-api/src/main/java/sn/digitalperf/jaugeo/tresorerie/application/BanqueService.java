package sn.digitalperf.jaugeo.tresorerie.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.tresorerie.domaine.Banque;
import sn.digitalperf.jaugeo.tresorerie.domaine.BanqueDepot;

/** Banques des versements : liste proposee a la saisie (banques actives) et choix des banques de la compagnie. */
@Service
@Transactional(readOnly = true)
public class BanqueService {

    public record BanqueVue(UUID id, String code, String nom, boolean actif, Integer version) {
    }

    private final BanqueDepot banques;

    public BanqueService(BanqueDepot banques) {
        this.banques = banques;
    }

    /** Banques actives, ou toutes les banques (pour choisir celles de la compagnie), par ordre alphabetique. */
    public List<BanqueVue> liste(boolean toutes) {
        return (toutes ? banques.findAllByOrderByNomAsc() : banques.findByActifTrueOrderByNomAsc()).stream()
                .map(BanqueService::vue).toList();
    }

    /** Ajoute une banque absente de la liste pre-remplie. */
    @Transactional
    public BanqueVue creer(String code, String nom) {
        String c = Banque.normaliser(code);
        banques.findByCode(c).ifPresent(b -> {
            throw ErreurMetier.conflit("CODE_EXISTANT", "Une banque porte déjà le code " + c + " (" + b.getNom() + ").");
        });
        return vue(banques.saveAndFlush(new Banque(c, nom)));
    }

    /** Active ou desactive une banque : une banque desactivee n'est plus proposee, ses versements restent intacts. */
    @Transactional
    public BanqueVue activer(UUID id, Integer version, boolean actif) {
        Banque b = banques.findByUuid(id).orElseThrow(() -> ErreurMetier.introuvable("Banque"));
        b.verifierVersion(version);
        b.activer(actif);
        return vue(banques.saveAndFlush(b));
    }

    private static BanqueVue vue(Banque b) {
        return new BanqueVue(b.getUuid(), b.getCode(), b.getNom(), b.isActif(), b.getVersion());
    }
}
