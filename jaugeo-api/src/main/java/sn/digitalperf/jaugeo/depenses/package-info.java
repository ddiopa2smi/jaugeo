/**
 * Depenses de station (M7) : natures de depense et plafonds, depenses payees sur la recette d'une journee,
 * justificatif, validation hierarchique au-dela du plafond. Les depenses autorisees sont deduites du montant
 * a verser ({@link sn.digitalperf.jaugeo.depenses.DepensesJournee}) ; le module prend part a la cloture
 * via {@link sn.digitalperf.jaugeo.exploitation.ParticipantCloture}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Dépenses")
package sn.digitalperf.jaugeo.depenses;
