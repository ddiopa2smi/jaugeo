package sn.digitalperf.jaugeo.referentiel.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.referentiel.application.Vues.BaremeVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.EvenementVue;
import sn.digitalperf.jaugeo.referentiel.application.Vues.PointBaremeVue;
import sn.digitalperf.jaugeo.referentiel.domaine.Bareme;
import sn.digitalperf.jaugeo.referentiel.domaine.Cuve;
import sn.digitalperf.jaugeo.referentiel.domaine.CuveDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement;
import sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipementDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Pistolet;
import sn.digitalperf.jaugeo.referentiel.domaine.PistoletDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.PointBareme;
import sn.digitalperf.jaugeo.referentiel.domaine.PointBaremeDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Pompe;
import sn.digitalperf.jaugeo.referentiel.domaine.PompeDepot;
import sn.digitalperf.jaugeo.referentiel.domaine.Station;
import sn.digitalperf.jaugeo.referentiel.domaine.StationDepot;
import sn.digitalperf.jaugeo.securite.PerimetreCourant;

/** Baremes de jaugeage des cuves et historique des equipements. */
@Service
@Transactional(readOnly = true)
public class EquipementService {

    private final CuveDepot cuves;
    private final PointBaremeDepot baremes;
    private final PistoletDepot pistolets;
    private final PompeDepot pompes;
    private final StationDepot stations;
    private final EvenementEquipementDepot evenements;
    private final PerimetreCourant perimetre;
    private final EntityManager em;
    private final Clock horloge;

    public EquipementService(CuveDepot cuves, PointBaremeDepot baremes, PistoletDepot pistolets, PompeDepot pompes,
                             StationDepot stations, EvenementEquipementDepot evenements, PerimetreCourant perimetre,
                             EntityManager em, Clock horloge) {
        this.cuves = cuves;
        this.baremes = baremes;
        this.pistolets = pistolets;
        this.pompes = pompes;
        this.stations = stations;
        this.evenements = evenements;
        this.perimetre = perimetre;
        this.em = em;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- Baremes

    public BaremeVue bareme(UUID cuveId) {
        Cuve cuve = cuveAccessible(cuveId);
        return vue(cuve, baremes.findByCuveIdOrderByHauteurMmAsc(cuve.getId()).stream().map(PointBareme::point).toList());
    }

    /** Remplace tout le bareme de la cuve (nouveau certificat). */
    @Transactional
    public BaremeVue remplacerBareme(UUID cuveId, String certificat, LocalDate dateCertificat,
                                     List<Bareme.Point> points, Integer version) {
        Cuve cuve = cuveAccessible(cuveId);
        cuve.verifierVersion(version);
        if (dateCertificat != null && dateCertificat.isAfter(LocalDate.now(horloge))) {
            throw ErreurMetier.regle("DATE_FUTURE", "La date du certificat ne peut pas être dans le futur.");
        }
        Bareme bareme = Bareme.valider(points);
        // Verrou de la cuve et version incrementee tout de suite : le bareme fait partie de la cuve,
        // deux remplacements concurrents ne peuvent pas se melanger.
        em.lock(cuve, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        cuve.appliquerBareme(bareme, certificat == null || certificat.isBlank() ? null : certificat.trim(), dateCertificat);
        baremes.supprimerPourCuve(cuve.getId());
        baremes.saveAll(bareme.points().stream().map(p -> new PointBareme(cuve.getId(), p)).toList());
        em.flush();
        return vue(cuve, bareme.points());
    }

    /** Conversion hauteur -> volume pour controle a l'ecran (calculette de jaugeage). */
    public BigDecimal volume(UUID cuveId, int hauteurMm) {
        Cuve cuve = cuveAccessible(cuveId);
        List<Bareme.Point> points = baremes.findByCuveIdOrderByHauteurMmAsc(cuve.getId()).stream().map(PointBareme::point).toList();
        if (points.isEmpty()) {
            throw ErreurMetier.regle("BAREME_ABSENT", "La cuve " + cuve.getCode() + " n'a pas encore de barème de jaugeage.");
        }
        return Bareme.valider(points).volumePour(hauteurMm);
    }

    private static BaremeVue vue(Cuve c, List<Bareme.Point> points) {
        return new BaremeVue(c.getUuid(), c.getCode(), c.getProduit().getLibelle(), c.getCapaciteLitres(),
                c.getBaremeCertificat(), c.getBaremeDate(), c.getVersion(),
                points.stream().map(p -> new PointBaremeVue(p.hauteurMm(), p.volumeLitres())).toList());
    }

    // ---------------------------------------------------------------- Evenements

    public List<EvenementVue> historique(UUID stationId) {
        Station s = stations.findByUuid(stationId).orElseThrow(() -> ErreurMetier.introuvable("Station"));
        perimetre.exigerStation(s.getId());
        return evenements.historiqueStation(s.getId()).stream().map(EvenementVue::de).toList();
    }

    @Transactional
    public EvenementVue surPistolet(UUID pistoletId, EvenementEquipement.Saisie saisie) {
        Pistolet p = pistolets.findByUuid(pistoletId).orElseThrow(() -> ErreurMetier.introuvable("Pistolet"));
        perimetre.exigerStation(p.getPompe().getStation().getId());
        // Verrou sur le pistolet : deux changements de compteur simultanes ne peuvent pas s'entrelacer
        em.lock(p, LockModeType.PESSIMISTIC_WRITE);
        EvenementEquipement dernier = evenements.dernierChangementCompteur(p.getId()).orElse(null);
        return EvenementVue.de(evenements.saveAndFlush(
                EvenementEquipement.surPistolet(p, saisie, dernier, horloge.instant())));
    }

    @Transactional
    public EvenementVue surPompe(UUID pompeId, EvenementEquipement.Saisie saisie) {
        Pompe p = pompes.findByUuid(pompeId).orElseThrow(() -> ErreurMetier.introuvable("Pompe"));
        perimetre.exigerStation(p.getStation().getId());
        return EvenementVue.de(evenements.saveAndFlush(EvenementEquipement.surPompe(p, saisie, horloge.instant())));
    }

    @Transactional
    public EvenementVue surCuve(UUID cuveId, EvenementEquipement.Saisie saisie) {
        Cuve c = cuveAccessible(cuveId);
        return EvenementVue.de(evenements.saveAndFlush(EvenementEquipement.surCuve(c, saisie, horloge.instant())));
    }

    private Cuve cuveAccessible(UUID cuveId) {
        Cuve cuve = cuves.findByUuid(cuveId).orElseThrow(() -> ErreurMetier.introuvable("Cuve"));
        perimetre.exigerStation(cuve.getStation().getId());
        return cuve;
    }
}
