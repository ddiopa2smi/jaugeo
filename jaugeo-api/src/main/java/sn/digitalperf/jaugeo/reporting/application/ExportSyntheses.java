package sn.digitalperf.jaugeo.reporting.application;

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
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.LigneStocks;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.LigneVentes;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.ProduitColonne;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseStocks;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.SyntheseVentes;
import sn.digitalperf.jaugeo.reporting.application.VuesSyntheses.TotalProduit;

/**
 * Exports des deux feuilles d'analyse : classeur Excel (formules H, Q, R et cumul conservees, pour que le controle de
 * gestion puisse poursuivre ses calculs) et PDF paysage pour diffusion.
 */
@Component
class ExportSyntheses {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    /** Colonnes monetaires de la feuille ventes, dans l'ordre du classeur (lettre du modele d'origine). */
    private static final String[] COLONNES_VENTES = {
        "Recettes du mois (E)", "Solde caisse ouverture (F)", "Créances début (G)", "Recettes totales à verser (H = E+F+G)",
        "Versements bancaires (I)", "Solde caisse gérant (J)", "Créances fin (K)", "Dépenses autorisées (L)",
        "Compléments citerne (M)", "Règlements reçus au siège (O)", "Total des versements (Q = I+J+K+L+M+O)",
        "Écart sur versement (R = Q−H)"};

    private static final String[] COLONNES_STOCKS = {
        "Station", "Cuve", "Produit", "Stock départ (L)", "Réceptions (L)", "Sorties (L)", "Stock théorique (L)",
        "Stock physique (L)", "Écart (L)", "Écart %", "Valeur écart (F)", "Tolérance %", "Niveau", "Jours"};

    static String titreMois(YearMonth mois) {
        String m = MOIS.format(mois);
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }

    // ---------------------------------------------------------------- Excel

    byte[] excel(SyntheseVentes ventes, SyntheseStocks stocks) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            feuilleVentes(wb, st, ventes);
            feuilleStocks(wb, st, stocks);
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void feuilleVentes(XSSFWorkbook wb, Styles st, SyntheseVentes v) {
        Sheet sh = wb.createSheet("Ventes et recettes");
        List<ProduitColonne> produits = v.produits();
        int nbCol = 1 + produits.size() + COLONNES_VENTES.length + 1;
        titre(sh, st, "ANALYSE VENTES & RECETTES — " + titreMois(v.mois()).toUpperCase(Locale.FRENCH), nbCol);
        Row entete = sh.createRow(2);
        entete.setHeightInPoints(42);
        int c = 0;
        texte(entete, c++, "Station", st.entete);
        for (ProduitColonne p : produits) {
            texte(entete, c++, p.libelle() + " (L)", st.entete);
        }
        for (String libelle : COLONNES_VENTES) {
            texte(entete, c++, libelle, st.entete);
        }
        texte(entete, c, "Observations", st.entete);

        int premiere = 3;
        int ligne = premiere;
        int colE = 1 + produits.size();
        for (LigneVentes l : v.lignes()) {
            Row r = sh.createRow(ligne);
            remplirVentes(r, st, l, produits, colE);
            ligne++;
        }
        Row cumul = sh.createRow(ligne);
        texte(cumul, 0, "CUMUL", st.totalTexte);
        for (int col = 1; col < colE + COLONNES_VENTES.length; col++) {
            String lettre = CellReference.convertNumToColString(col);
            Cell cell = cumul.createCell(col);
            cell.setCellStyle(col < colE ? st.totalLitres : st.totalFrancs);
            if (ligne > premiere) {
                cell.setCellFormula("SUM(" + lettre + (premiere + 1) + ":" + lettre + ligne + ")");
            } else {
                cell.setCellValue(0);
            }
        }
        texte(cumul, colE + COLONNES_VENTES.length, "", st.totalTexte);

        sh.setColumnWidth(0, 22 * 256);
        for (int col = 1; col < nbCol - 1; col++) {
            sh.setColumnWidth(col, 15 * 256);
        }
        sh.setColumnWidth(nbCol - 1, 60 * 256);
        sh.createFreezePane(1, 3);
    }

    /** Une ligne station : valeurs, puis H, Q et R en formules comme dans le classeur d'origine. */
    private static void remplirVentes(Row r, Styles st, LigneVentes l, List<ProduitColonne> produits, int colE) {
        texte(r, 0, l.stationCode() + " — " + l.stationNom(), st.texte);
        int c = 1;
        for (ProduitColonne p : produits) {
            nombre(r, c++, l.litres().getOrDefault(p.code(), BigDecimal.ZERO), st.litres);
        }
        int n = r.getRowNum() + 1;
        String e = CellReference.convertNumToColString(colE);
        String f = CellReference.convertNumToColString(colE + 1);
        String g = CellReference.convertNumToColString(colE + 2);
        String h = CellReference.convertNumToColString(colE + 3);
        String i = CellReference.convertNumToColString(colE + 4);
        String j = CellReference.convertNumToColString(colE + 5);
        String k = CellReference.convertNumToColString(colE + 6);
        String lL = CellReference.convertNumToColString(colE + 7);
        String m = CellReference.convertNumToColString(colE + 8);
        String o = CellReference.convertNumToColString(colE + 9);
        String q = CellReference.convertNumToColString(colE + 10);
        nombre(r, colE, l.recettes(), st.francs);
        nombre(r, colE + 1, l.soldeOuverture(), st.francs);
        nombre(r, colE + 2, l.creancesDebut(), st.francs);
        formule(r, colE + 3, e + n + "+" + f + n + "+" + g + n, st.francsCalcul);
        nombre(r, colE + 4, l.versements(), st.francs);
        nombre(r, colE + 5, l.soldeCaisse(), st.francs);
        nombre(r, colE + 6, l.creancesFin(), st.francs);
        nombre(r, colE + 7, l.depenses(), st.francs);
        nombre(r, colE + 8, l.complements(), st.francs);
        nombre(r, colE + 9, l.reglementsSiege(), st.francs);
        formule(r, colE + 10, i + n + "+" + j + n + "+" + k + n + "+" + lL + n + "+" + m + n + "+" + o + n, st.francsCalcul);
        formule(r, colE + 11, q + n + "-" + h + n, st.francsCalcul);
        texte(r, colE + 12, observations(l), st.texteLong);
    }

    static String observations(LigneVentes l) {
        List<String> r = new ArrayList<>();
        if (l.commentaire() != null) {
            r.add(l.commentaire());
        }
        if (l.observation() != null) {
            r.add(l.observation());
        }
        return String.join(" — ", r);
    }

    private static void feuilleStocks(XSSFWorkbook wb, Styles st, SyntheseStocks s) {
        Sheet sh = wb.createSheet("Stocks");
        titre(sh, st, "ANALYSE DES STOCKS — " + titreMois(s.mois()).toUpperCase(Locale.FRENCH), COLONNES_STOCKS.length);
        Row entete = sh.createRow(2);
        entete.setHeightInPoints(30);
        for (int c = 0; c < COLONNES_STOCKS.length; c++) {
            texte(entete, c, COLONNES_STOCKS[c], st.entete);
        }
        int ligne = 3;
        for (LigneStocks l : s.lignes()) {
            Row r = sh.createRow(ligne++);
            texte(r, 0, l.stationCode(), st.texte);
            texte(r, 1, l.cuveCode(), st.texte);
            texte(r, 2, l.produitLibelle(), st.texte);
            nombre(r, 3, l.stockDepart(), st.litres);
            nombre(r, 4, l.receptions(), st.litres);
            nombre(r, 5, l.sorties(), st.litres);
            nombre(r, 6, l.stockTheorique(), st.litres);
            nombre(r, 7, l.stockPhysique(), st.litres);
            nombre(r, 8, l.ecartLitres(), st.litres);
            nombre(r, 9, l.ecartPct(), st.pct);
            nombre(r, 10, l.ecartValeur(), st.francs);
            nombre(r, 11, l.tolerancePct(), st.pct);
            texte(r, 12, l.niveau(), st.texte);
            nombre(r, 13, BigDecimal.valueOf(l.jours()), st.entier);
        }
        ligne++;
        Row t = sh.createRow(ligne++);
        texte(t, 0, "TOTAL PAR PRODUIT", st.totalTexte);
        for (TotalProduit p : s.totaux()) {
            Row r = sh.createRow(ligne++);
            texte(r, 0, "Réseau", st.totalTexte);
            texte(r, 2, p.produitLibelle(), st.totalTexte);
            nombre(r, 4, p.receptions(), st.totalLitres);
            nombre(r, 5, p.sorties(), st.totalLitres);
            nombre(r, 8, p.ecartLitres(), st.totalLitres);
            nombre(r, 9, p.ecartPct(), st.pct);
            nombre(r, 10, p.ecartValeur(), st.totalFrancs);
        }
        sh.setColumnWidth(0, 14 * 256);
        sh.setColumnWidth(1, 8 * 256);
        sh.setColumnWidth(2, 18 * 256);
        for (int c = 3; c < COLONNES_STOCKS.length; c++) {
            sh.setColumnWidth(c, 14 * 256);
        }
        sh.createFreezePane(0, 3);
    }

    private static void titre(Sheet sh, Styles st, String titre, int nbCol) {
        Row r = sh.createRow(0);
        r.setHeightInPoints(22);
        texte(r, 0, titre, st.titre);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(1, nbCol - 1)));
    }

    private static void texte(Row r, int c, String v, CellStyle s) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        cell.setCellStyle(s);
    }

    private static void nombre(Row r, int c, BigDecimal v, CellStyle s) {
        Cell cell = r.createCell(c);
        if (v != null) {
            cell.setCellValue(v.doubleValue());
        }
        cell.setCellStyle(s);
    }

    private static void formule(Row r, int c, String f, CellStyle s) {
        Cell cell = r.createCell(c);
        cell.setCellFormula(f);
        cell.setCellStyle(s);
    }

    /** Styles du classeur. */
    private static final class Styles {
        final CellStyle titre;
        final CellStyle entete;
        final CellStyle texte;
        final CellStyle texteLong;
        final CellStyle litres;
        final CellStyle francs;
        final CellStyle francsCalcul;
        final CellStyle pct;
        final CellStyle entier;
        final CellStyle totalTexte;
        final CellStyle totalLitres;
        final CellStyle totalFrancs;

        Styles(XSSFWorkbook wb) {
            var gras = wb.createFont();
            gras.setBold(true);
            var grand = wb.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 14);
            short fmtLitres = wb.createDataFormat().getFormat("#,##0.00");
            short fmtFrancs = wb.createDataFormat().getFormat("#,##0;-#,##0");
            short fmtPct = wb.createDataFormat().getFormat("0.00");
            short fmtEntier = wb.createDataFormat().getFormat("0");

            titre = wb.createCellStyle();
            titre.setFont(grand);
            entete = bordure(wb.createCellStyle());
            entete.setFont(gras);
            entete.setWrapText(true);
            entete.setAlignment(HorizontalAlignment.CENTER);
            entete.setVerticalAlignment(VerticalAlignment.CENTER);
            entete.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            texte = bordure(wb.createCellStyle());
            texteLong = bordure(wb.createCellStyle());
            texteLong.setWrapText(true);
            litres = nombre(wb, fmtLitres);
            francs = nombre(wb, fmtFrancs);
            francsCalcul = nombre(wb, fmtFrancs);
            francsCalcul.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            francsCalcul.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            pct = nombre(wb, fmtPct);
            entier = nombre(wb, fmtEntier);
            totalTexte = bordure(wb.createCellStyle());
            totalTexte.setFont(gras);
            totalLitres = nombre(wb, fmtLitres);
            totalLitres.setFont(gras);
            totalFrancs = nombre(wb, fmtFrancs);
            totalFrancs.setFont(gras);
        }

        private static CellStyle nombre(XSSFWorkbook wb, short format) {
            CellStyle s = bordure(wb.createCellStyle());
            s.setDataFormat(format);
            return s;
        }

        private static CellStyle bordure(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }

    // ---------------------------------------------------------------- PDF

    byte[] pdf(SyntheseVentes ventes, SyntheseStocks stocks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 20, 20, 24, 24);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font entete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6);
        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 6);
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6);

        doc.add(new Paragraph("ANALYSE VENTES & RECETTES — " + titreMois(ventes.mois()).toUpperCase(Locale.FRENCH), titre));
        doc.add(new Paragraph(" ", corps));
        List<ProduitColonne> produits = ventes.produits();
        int nb = 1 + produits.size() + COLONNES_VENTES.length + 1;
        PdfPTable t = new PdfPTable(nb);
        t.setWidthPercentage(100);
        float[] largeurs = new float[nb];
        java.util.Arrays.fill(largeurs, 1f);
        largeurs[0] = 1.4f;
        largeurs[nb - 1] = 3f;
        t.setWidths(largeurs);
        t.setHeaderRows(1);
        cellule(t, "Station", entete, Element.ALIGN_CENTER, true);
        produits.forEach(p -> cellule(t, p.libelle() + " (L)", entete, Element.ALIGN_CENTER, true));
        for (String c : COLONNES_VENTES) {
            cellule(t, c, entete, Element.ALIGN_CENTER, true);
        }
        cellule(t, "Observations", entete, Element.ALIGN_CENTER, true);
        for (LigneVentes l : ventes.lignes()) {
            ligneVentes(t, l, produits, corps);
        }
        ligneVentes(t, ventes.cumul(), produits, gras);
        doc.add(t);

        doc.newPage();
        doc.add(new Paragraph("ANALYSE DES STOCKS — " + titreMois(stocks.mois()).toUpperCase(Locale.FRENCH), titre));
        doc.add(new Paragraph(" ", corps));
        PdfPTable s = new PdfPTable(COLONNES_STOCKS.length);
        s.setWidthPercentage(100);
        s.setHeaderRows(1);
        for (String c : COLONNES_STOCKS) {
            cellule(s, c, entete, Element.ALIGN_CENTER, true);
        }
        for (LigneStocks l : stocks.lignes()) {
            cellule(s, l.stationCode(), corps, Element.ALIGN_LEFT, false);
            cellule(s, l.cuveCode(), corps, Element.ALIGN_LEFT, false);
            cellule(s, l.produitLibelle(), corps, Element.ALIGN_LEFT, false);
            for (BigDecimal v : new BigDecimal[] {l.stockDepart(), l.receptions(), l.sorties(), l.stockTheorique(), l.stockPhysique(),
                    l.ecartLitres()}) {
                cellule(s, litres(v), corps, Element.ALIGN_RIGHT, false);
            }
            cellule(s, pct(l.ecartPct()), corps, Element.ALIGN_RIGHT, false);
            cellule(s, francs(l.ecartValeur()), corps, Element.ALIGN_RIGHT, false);
            cellule(s, pct(l.tolerancePct()), corps, Element.ALIGN_RIGHT, false);
            cellule(s, l.niveau(), corps, Element.ALIGN_LEFT, false);
            cellule(s, String.valueOf(l.jours()), corps, Element.ALIGN_RIGHT, false);
        }
        for (TotalProduit p : stocks.totaux()) {
            cellule(s, "Réseau", gras, Element.ALIGN_LEFT, false);
            cellule(s, "", gras, Element.ALIGN_LEFT, false);
            cellule(s, p.produitLibelle(), gras, Element.ALIGN_LEFT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
            cellule(s, litres(p.receptions()), gras, Element.ALIGN_RIGHT, false);
            cellule(s, litres(p.sorties()), gras, Element.ALIGN_RIGHT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
            cellule(s, litres(p.ecartLitres()), gras, Element.ALIGN_RIGHT, false);
            cellule(s, pct(p.ecartPct()), gras, Element.ALIGN_RIGHT, false);
            cellule(s, francs(p.ecartValeur()), gras, Element.ALIGN_RIGHT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
            cellule(s, "", gras, Element.ALIGN_RIGHT, false);
        }
        doc.add(s);
        doc.close();
        return out.toByteArray();
    }

    private static void ligneVentes(PdfPTable t, LigneVentes l, List<ProduitColonne> produits, Font f) {
        cellule(t, l.stationId() == null ? l.stationCode() : l.stationCode() + " — " + l.stationNom(), f, Element.ALIGN_LEFT, false);
        produits.forEach(p -> cellule(t, litres(l.litres().getOrDefault(p.code(), BigDecimal.ZERO)), f, Element.ALIGN_RIGHT, false));
        for (BigDecimal v : new BigDecimal[] {l.recettes(), l.soldeOuverture(), l.creancesDebut(), l.aVerser(), l.versements(),
                l.soldeCaisse(), l.creancesFin(), l.depenses(), l.complements(), l.reglementsSiege(), l.totalVersements(), l.ecart()}) {
            cellule(t, francs(v), f, Element.ALIGN_RIGHT, false);
        }
        cellule(t, observations(l), f, Element.ALIGN_LEFT, false);
    }

    private static void cellule(PdfPTable t, String texte, Font f, int alignement, boolean entete) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, f));
        c.setHorizontalAlignment(alignement);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(2);
        if (entete) {
            c.setBackgroundColor(new Color(230, 230, 230));
        }
        t.addCell(c);
    }

    private static DecimalFormat format(String motif) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        return new DecimalFormat(motif, s);
    }

    static String francs(BigDecimal v) {
        return v == null ? "" : format("#,##0").format(v);
    }

    static String litres(BigDecimal v) {
        return v == null ? "" : format("#,##0.00").format(v);
    }

    static String pct(BigDecimal v) {
        return v == null ? "" : format("0.00").format(v);
    }
}
