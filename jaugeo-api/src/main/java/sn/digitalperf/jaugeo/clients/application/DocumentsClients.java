package sn.digitalperf.jaugeo.clients.application;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureDetail;
import sn.digitalperf.jaugeo.clients.application.VuesClients.FactureVue;
import sn.digitalperf.jaugeo.clients.application.VuesClients.LigneFacture;
import sn.digitalperf.jaugeo.clients.domaine.Client;
import sn.digitalperf.jaugeo.clients.domaine.Imputation.Reste;
import sn.digitalperf.jaugeo.commun.Libelles;

/** Documents PDF remis aux clients : facture periodique et lettre de relance. */
@Component
class DocumentsClients {

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font GRAS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CORPS = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font PETIT = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private final String societe;
    private final String adresseSociete;

    DocumentsClients(@Value("${jaugeo.clients.societe:Société exploitante}") String societe,
                     @Value("${jaugeo.clients.adresse-societe:}") String adresseSociete) {
        this.societe = societe;
        this.adresseSociete = adresseSociete;
    }

    byte[] facture(FactureDetail d, Client c) {
        FactureVue f = d.facture();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        entete(doc, c);
        doc.add(new Paragraph("Facture n° " + f.numero() + (f.statut().equals("ANNULEE") ? " (ANNULÉE)" : ""), TITRE));
        doc.add(new Paragraph("Date : " + Libelles.date(f.dateFacture()) + "   ·   Période : du " + Libelles.date(f.periodeDebut())
                + " au " + Libelles.date(f.periodeFin()) + "   ·   Échéance : " + Libelles.date(f.dateEcheance()), CORPS));
        doc.add(new Paragraph(" "));
        float[] largeurs = {9, 7, 9, 8, 8, 9, 7, 9, 12, 12};
        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        for (String e : List.of("Date", "Station", "Bon", "Produit", "Litres", "Prix pompe", "Remise", "Prix net", "Montant", "Immat.")) {
            cellule(t, e, GRAS, Element.ALIGN_CENTER, true);
        }
        BigDecimal litres = BigDecimal.ZERO;
        BigDecimal remises = BigDecimal.ZERO;
        for (LigneFacture l : d.lignes()) {
            cellule(t, Libelles.date(l.date()), PETIT, Element.ALIGN_LEFT, false);
            cellule(t, l.stationCode(), PETIT, Element.ALIGN_LEFT, false);
            cellule(t, l.numero(), PETIT, Element.ALIGN_LEFT, false);
            cellule(t, l.produitCode(), PETIT, Element.ALIGN_LEFT, false);
            cellule(t, l.litres().stripTrailingZeros().toPlainString(), PETIT, Element.ALIGN_RIGHT, false);
            cellule(t, prix(l.prixPompe()), PETIT, Element.ALIGN_RIGHT, false);
            cellule(t, l.remise().signum() == 0 ? "" : prix(l.remise()), PETIT, Element.ALIGN_RIGHT, false);
            cellule(t, prix(l.prixUnitaire()), PETIT, Element.ALIGN_RIGHT, false);
            cellule(t, Libelles.francs(l.montant()), PETIT, Element.ALIGN_RIGHT, false);
            cellule(t, l.immatriculation(), PETIT, Element.ALIGN_LEFT, false);
            litres = litres.add(l.litres());
            remises = remises.add(l.remise().multiply(l.litres()));
        }
        PdfPCell total = new PdfPCell(new Phrase("Total : " + d.lignes().size() + " bons, " + litres.stripTrailingZeros().toPlainString()
                + " L" + (remises.signum() > 0 ? ", remises " + Libelles.francs(remises) : ""), GRAS));
        total.setColspan(8);
        t.addCell(total);
        PdfPCell montant = new PdfPCell(new Phrase(Libelles.francs(f.montant()), GRAS));
        montant.setColspan(2);
        montant.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(montant);
        doc.add(t);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Montant à payer : " + Libelles.francs(f.montant()) + " CFA, au plus tard le "
                + Libelles.date(f.dateEcheance()) + ".", GRAS));
        doc.add(new Paragraph("Montants en francs CFA. Le paiement se fait par virement, chèque ou espèces au siège.", PETIT));
        doc.close();
        return out.toByteArray();
    }

    /** Lettre de relance : niveau 1 rappel, 2 relance, 3 mise en demeure ; liste des documents echus non regles. */
    byte[] relance(Client c, LocalDate date, int niveau, BigDecimal echu, List<Reste> restes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();
        entete(doc, c);
        String objet = switch (niveau) {
            case 1 -> "Rappel d'échéance";
            case 2 -> "Relance pour impayé";
            default -> "Mise en demeure de payer";
        };
        doc.add(new Paragraph("Dakar, le " + Libelles.date(date), CORPS));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Objet : " + objet, GRAS));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Madame, Monsieur,", CORPS));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Sauf erreur de notre part, les sommes suivantes, arrivées à échéance, restent impayées à ce jour :", CORPS));
        doc.add(new Paragraph(" "));
        PdfPTable t = new PdfPTable(new float[] {30, 20, 20, 30});
        t.setWidthPercentage(80);
        for (String e : List.of("Document", "Date", "Échéance", "Reste dû")) {
            cellule(t, e, GRAS, Element.ALIGN_CENTER, true);
        }
        for (Reste r : restes) {
            if (!r.echue(date)) {
                continue;
            }
            cellule(t, r.dette().reference(), CORPS, Element.ALIGN_LEFT, false);
            cellule(t, Libelles.date(r.dette().date()), CORPS, Element.ALIGN_LEFT, false);
            cellule(t, Libelles.date(r.dette().echeance()), CORPS, Element.ALIGN_LEFT, false);
            cellule(t, Libelles.francs(r.reste()), CORPS, Element.ALIGN_RIGHT, false);
        }
        PdfPCell lib = new PdfPCell(new Phrase("Total échu", GRAS));
        lib.setColspan(3);
        t.addCell(lib);
        cellule(t, Libelles.francs(echu), GRAS, Element.ALIGN_RIGHT, false);
        doc.add(t);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(switch (niveau) {
            case 1 -> "Nous vous remercions de bien vouloir procéder à leur règlement dans les meilleurs délais. Si votre "
                    + "paiement a été effectué entre-temps, veuillez ne pas tenir compte de ce rappel.";
            case 2 -> "Malgré notre précédent rappel, ces sommes ne nous sont pas parvenues. Nous vous prions de les régler "
                    + "sous huit jours. À défaut, les nouveaux bons à crédit seront suspendus.";
            default -> "Malgré nos relances, ces sommes restent impayées. Nous vous mettons en demeure de les régler sous huit "
                    + "jours à compter de la réception de la présente. À défaut, nous engagerons le recouvrement par toute voie "
                    + "de droit, y compris l'appel de la caution bancaire.";
        }, CORPS));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Veuillez agréer, Madame, Monsieur, nos salutations distinguées.", CORPS));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("La Direction", GRAS));
        doc.close();
        return out.toByteArray();
    }

    private void entete(Document doc, Client c) {
        doc.add(new Paragraph(societe, GRAS));
        if (adresseSociete != null && !adresseSociete.isBlank()) {
            doc.add(new Paragraph(adresseSociete, PETIT));
        }
        Paragraph client = new Paragraph();
        client.setAlignment(Element.ALIGN_RIGHT);
        client.add(new Phrase(c.getNom() + "\n", GRAS));
        client.add(new Phrase("Client " + c.getCode()
                + (c.getNinea() == null ? "" : " · NINEA " + c.getNinea())
                + (c.getAdresse() == null ? "" : "\n" + c.getAdresse())
                + (c.getTelephone() == null ? "" : "\n" + c.getTelephone()), CORPS));
        doc.add(client);
        doc.add(new Paragraph(" "));
    }

    private static String prix(BigDecimal p) {
        return p.stripTrailingZeros().scale() <= 0 ? Libelles.francs(p) : p.toPlainString().replace('.', ',') + " F";
    }

    private static void cellule(PdfPTable t, String texte, Font f, int alignement, boolean entete) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, f));
        c.setHorizontalAlignment(alignement);
        if (entete) {
            c.setGrayFill(0.9f);
        }
        t.addCell(c);
    }
}
