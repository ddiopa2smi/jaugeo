/**
 * Reprise de l'existant : outil en ligne de commande qui lit le classeur « SUIVI DES STATIONS », rejoue chaque
 * journee dans Jaugeo par l'API (memes regles qu'a l'ecran) et rapproche les valeurs recalculees de celles du
 * classeur. Aucun composant Spring : le module ne depend d'aucun autre module, il parle a l'API par HTTP.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reprise du classeur")
package sn.digitalperf.jaugeo.reprise;
