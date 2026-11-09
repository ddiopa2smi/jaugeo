package sn.digitalperf.jaugeo.referentiel.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.digitalperf.jaugeo.referentiel.domaine.Cuve;
import sn.digitalperf.jaugeo.referentiel.domaine.EvenementEquipement;
import sn.digitalperf.jaugeo.referentiel.domaine.Pistolet;
import sn.digitalperf.jaugeo.referentiel.domaine.Pompe;
import sn.digitalperf.jaugeo.referentiel.domaine.Produit;
import sn.digitalperf.jaugeo.referentiel.domaine.Region;
import sn.digitalperf.jaugeo.referentiel.domaine.Station;

/** Representations renvoyees par l'API du referentiel. */
public final class Vues {

    private Vues() {
    }

    public record RegionVue(String code, String nom) {
        public static RegionVue de(Region r) {
            return new RegionVue(r.getCode(), r.getNom());
        }
    }

    /** numero = identifiant technique, utilise pour definir le perimetre des utilisateurs. */
    public record StationVue(UUID id, Long numero, String code, String nom, Station.TypeStation type,
                             String regionCode, String regionNom, String localite, BigDecimal latitude,
                             BigDecimal longitude, Station.ModeExploitation modeExploitation,
                             LocalDate dateOuverture, boolean actif, Integer version) {
        public static StationVue de(Station s) {
            return new StationVue(s.getUuid(), s.getId(), s.getCode(), s.getNom(), s.getType(),
                    s.getRegion().getCode(), s.getRegion().getNom(), s.getLocalite(), s.getLatitude(),
                    s.getLongitude(), s.getModeExploitation(), s.getDateOuverture(), s.isActif(), s.getVersion());
        }
    }

    public record ProduitVue(UUID id, String code, String libelle, Produit.Famille famille, Produit.Unite unite,
                             boolean suiviJaugeage, BigDecimal tauxTva, boolean actif, BigDecimal toleranceEcartPct,
                             Integer version) {
        public static ProduitVue de(Produit p) {
            return new ProduitVue(p.getUuid(), p.getCode(), p.getLibelle(), p.getFamille(), p.getUnite(),
                    p.isSuiviJaugeage(), p.getTauxTva(), p.isActif(), p.getToleranceEcartPct(), p.getVersion());
        }
    }

    public record CuveVue(UUID id, String code, String produitCode, String produitLibelle,
                          BigDecimal capaciteLitres, BigDecimal creuxSecuriteLitres, boolean actif, Integer version,
                          int baremePoints, String baremeCertificat, LocalDate baremeDate, Integer seuilEauMm,
                          BigDecimal seuilReapproLitres) {
        public static CuveVue de(Cuve c, long baremePoints) {
            return new CuveVue(c.getUuid(), c.getCode(), c.getProduit().getCode(), c.getProduit().getLibelle(),
                    c.getCapaciteLitres(), c.getCreuxSecuriteLitres(), c.isActif(), c.getVersion(),
                    (int) baremePoints, c.getBaremeCertificat(), c.getBaremeDate(), c.getSeuilEauMm(),
                    c.getSeuilReapproLitres());
        }
    }

    public record PointBaremeVue(int hauteurMm, BigDecimal volumeLitres) {
    }

    public record BaremeVue(UUID cuveId, String cuveCode, String produitLibelle, BigDecimal capaciteLitres,
                            String certificat, LocalDate dateCertificat, Integer cuveVersion,
                            List<PointBaremeVue> points) {
    }

    /** Evenement d'equipement ; equipement = libelle lisible (« Pistolet G1 · pompe P1 »). */
    public record EvenementVue(UUID id, String cible, UUID equipementId, String equipement, String type,
                               Instant date, BigDecimal indexAvant, BigDecimal indexApres, String referenceScelles,
                               String intervenant, LocalDate prochaineVerification, String commentaire,
                               String creePar) {
        public static EvenementVue de(EvenementEquipement e) {
            String cible;
            UUID equipementId;
            String libelle;
            if (e.getPistolet() != null) {
                cible = "PISTOLET";
                equipementId = e.getPistolet().getUuid();
                libelle = "Pistolet " + e.getPistolet().getCode() + " · pompe " + e.getPistolet().getPompe().getCode();
            } else if (e.getPompe() != null) {
                cible = "POMPE";
                equipementId = e.getPompe().getUuid();
                libelle = "Pompe " + e.getPompe().getCode();
            } else {
                cible = "CUVE";
                equipementId = e.getCuve().getUuid();
                libelle = "Cuve " + e.getCuve().getCode() + " · " + e.getCuve().getProduit().getLibelle();
            }
            return new EvenementVue(e.getUuid(), cible, equipementId, libelle, e.getType().name(), e.getDate(),
                    e.getIndexAvant(), e.getIndexApres(), e.getReferenceScelles(), e.getIntervenant(),
                    e.getProchaineVerification(), e.getCommentaire(), e.getCreePar());
        }
    }

    public record PistoletVue(UUID id, String code, String libelle, UUID cuveId, String cuveCode,
                              String produitLibelle, BigDecimal indexMiseEnService, boolean actif, Integer version) {
        public static PistoletVue de(Pistolet p) {
            return new PistoletVue(p.getUuid(), p.getCode(), p.getLibelle(), p.getCuve().getUuid(),
                    p.getCuve().getCode(), p.getCuve().getProduit().getLibelle(), p.getIndexMiseEnService(),
                    p.isActif(), p.getVersion());
        }
    }

    public record PompeVue(UUID id, String code, String marque, String numeroSerie, boolean actif, Integer version,
                           List<PistoletVue> pistolets) {
        public static PompeVue de(Pompe p, List<PistoletVue> pistolets) {
            return new PompeVue(p.getUuid(), p.getCode(), p.getMarque(), p.getNumeroSerie(), p.isActif(),
                    p.getVersion(), pistolets);
        }
    }

    public record EquipementsVue(StationVue station, List<CuveVue> cuves, List<PompeVue> pompes) {
    }
}
