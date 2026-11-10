/**
 * Personnel de station (M9) : employes et historique de leurs affectations, planning par poste (matin, soir,
 * nuit) avec reconduction et rotation, feuilles de caisse par pompiste dans la journee (releve d'index a chaque
 * changement de poste, un pompiste par pistolet) et ecart individuel fige a la cloture. Les feuilles s'activent
 * station par station ; l'ecart du pompiste est un suivi, le gerant reste redevable envers la societe.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Personnel")
package sn.digitalperf.jaugeo.personnel;
