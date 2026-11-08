/**
 * Tresorerie (M5) : montant a verser de chaque journee (fige a la cloture), versements bancaires justifies par
 * leur bordereau et verifies par la comptabilite, solde de caisse du gerant reporte jour apres jour (ecart de
 * versement), arretes de caisse et manquants du gerant. Rapprochements bancaire et mobile : lot 2.
 * Prend part a la cloture des journees via {@link sn.digitalperf.jaugeo.exploitation.ParticipantCloture}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Trésorerie")
package sn.digitalperf.jaugeo.tresorerie;
