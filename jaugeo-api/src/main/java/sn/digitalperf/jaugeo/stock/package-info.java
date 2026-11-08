/**
 * Stocks (M4) : jaugeages de cuve, receptions citerne liees au BL, stock theorique, ecarts de stock alertes
 * au-dela de la tolerance du produit, complements citerne a la charge du transporteur.
 * Prend part a la cloture des journees via {@link sn.digitalperf.jaugeo.exploitation.ParticipantCloture}.
 * Lubrifiants et boutique : lot ulterieur.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Stocks")
package sn.digitalperf.jaugeo.stock;
