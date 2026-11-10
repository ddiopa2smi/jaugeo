/**
 * Approvisionnement (M8) : proposition de commande selon l'autonomie des cuves et le delai du depot, tournees
 * citernes a plusieurs arrets (commandee, chargee, en route, depotee a la reception du gerant), transporteurs
 * (contrat, tolerance de manquant, penalites, note), controle des factures fournisseur contre BL et quantites
 * recues, et bon a payer.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Approvisionnement")
package sn.digitalperf.jaugeo.approvisionnement;
