/**
 * Clients en compte (M6) : clients, bons a credit saisis dans la journee (recouvrement par la direction ou par le
 * gerant, plafond d'encours), reprises de creances et reglements, creances par client et par station.
 * Les bons et les recouvrements en especes entrent dans le montant a verser
 * ({@link sn.digitalperf.jaugeo.clients.ComptesClients}) ; le module prend part a la cloture via
 * {@link sn.digitalperf.jaugeo.exploitation.ParticipantCloture}. Facturation, balance agee, cartes : lot 2.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Clients")
package sn.digitalperf.jaugeo.clients;
