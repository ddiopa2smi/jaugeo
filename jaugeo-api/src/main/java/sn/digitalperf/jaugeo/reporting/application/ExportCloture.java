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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.CompteReseau;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.CompteStation;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.ProduitCompte;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviBudget;
import sn.digitalperf.jaugeo.reporting.application.VuesCloture.SuiviMois;

/**
 * Exports du compte d'exploitation mensuel : classeur Excel (compte par station et suivi budgetaire du reseau)
 * et PDF paysage du compte.
 */
@Component
class ExportCloture {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter MOIS_COURT = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH);

    /** Ligne du compte : libelle, valeur par station, mise en avant (sous-total), valeur en pourcentage. */
    record LigneCompte(String libelle, Function<CompteStation, BigDecimal> valeur, boolean total, boolean pourcentage) {
    }

    /** Lignes du compte d'exploitation, dans l'ordre de lecture : du chiffre d'affaires au resultat. */
    static List<LigneCompte> lignes(CompteReseau c) {
        List<LigneCompte> r = new ArrayList<>();
        for (ProduitCompte p : c.total().produits()) {
            if (p.chiffreAffaires().signum() != 0) {
                r.add(new LigneCompte("Ventes " + p.libelle(), s -> produit(s, p.code(), ProduitCompte::chiffreAffaires), false, false));
            }
        }
        r.add(new LigneCompte("Chiffre d'affaires carburants", CompteStation::chiffreAffaires, true, false));
        r.add(new LigneCompte("Coût d'achat des carburants vendus", CompteStation::coutAchat, false, false));
        r.add(new LigneCompte("Marge sur carburants", CompteStation::margeCarburants, true, false));
        r.add(new LigneCompte("Ventes lubrifiants et boutique", CompteStation::ventesArticles, false, false));
        r.add(new LigneCompte("Coût des articles vendus (CMUP)", CompteStation::coutArticles, false, false));
        r.add(new LigneCompte("Marge sur lubrifiants et boutique", CompteStation::margeArticles, false, false));
        r.add(new LigneCompte("Remises accordées aux clients", CompteStation::remises, false, false));
        r.add(new LigneCompte("MARGE BRUTE", CompteStation::margeBrute, true, false));
        r.add(new LigneCompte("Taux de marge (%)", CompteStation::tauxMarge, false, true));
        r.add(new LigneCompte("Écarts de stock au coût d'achat", CompteStation::ecartsStock, false, false));
        r.add(new LigneCompte("Dépenses d'exploitation", CompteStation::depenses, false, false));
        r.add(new LigneCompte("Régularisations sur mois clos", CompteStation::regularisations, false, false));
        r.add(new LigneCompte("RÉSULTAT", CompteStation::resultat, true, false));
        return r;
    }

    private static BigDecimal produit(CompteStation s, String code, Function<ProduitCompte, BigDecimal> f) {
        return s.produits().stream().filter(p -> p.code().equals(code)).map(f).findFirst().orElse(BigDecimal.ZERO);
    }

    static String titreMois(java.time.YearMonth mois) {
        String m = MOIS.format(mois);
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }

    private static String etat(CompteStation s) {
        return s.clos() ? "Clôturé" : "Provisoire";
    }

    /** Avertissement sur les prix d'achat manquants ; null si tous les couts sont connus. */
    static String avertissement(CompteReseau c) {
        return c.total().coutComplet() ? null : "Prix d'achat convenu manquant pour : " + String.join(", ", c.total().prixManquants())
                + ". La marge ne couvre que les produits dont le coût est connu.";
    }

    // ---------------------------------------------------------------- Excel

    byte[] excel(CompteReseau compte, SuiviBudget budget) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            feuilleCompte(wb, st, compte);
            feuilleBudget(wb, st, budget);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void feuilleCompte(XSSFWorkbook wb, Styles st, CompteReseau c) {
        Sheet sh = wb.createSheet("Compte d'exploitation");
        int nbCol = c.stations().size() + 2;
        titre(sh, st, "COMPTE D'EXPLOITATION — " + titreMois(c.mois()).toUpperCase(Locale.FRENCH), nbCol);
        Row entete = sh.createRow(2);
        entete.setHeightInPoints(30);
        texte(entete, 0, "Rubrique (F CFA)", st.entete);
        int col = 1;
        for (CompteStation s : c.stations()) {
            texte(entete, col++, s.stationNom(), st.entete);
        }
        texte(entete, col, "Réseau", st.entete);
        int ligne = 3;
        for (LigneCompte l : lignes(c)) {
            Row r = sh.createRow(ligne++);
            texte(r, 0, l.libelle(), l.total() ? st.totalTexte : st.texte);
            CellStyle style = l.pourcentage() ? st.pct : l.total() ? st.totalFrancs : st.francs;
            col = 1;
            for (CompteStation s : c.stations()) {
                nombre(r, col++, l.valeur().apply(s), style);
            }
            nombre(r, col, l.valeur().apply(c.total()), style);
        }
        Row etat = sh.createRow(ligne++);
        texte(etat, 0, "État du mois", st.texte);
        col = 1;
        for (CompteStation s : c.stations()) {
            texte(etat, col++, etat(s), st.texte);
        }
        texte(etat, col, etat(c.total()), st.texte);
        String avertissement = avertissement(c);
        if (avertissement != null) {
            texte(sh.createRow(ligne + 1), 0, avertissement, st.note);
        }
        sh.setColumnWidth(0, 40 * 256);
        for (int i = 1; i < nbCol; i++) {
            sh.setColumnWidth(i, 18 * 256);
        }
        sh.createFreezePane(1, 3);
    }

    private static void feuilleBudget(XSSFWorkbook wb, Styles st, SuiviBudget b) {
        Sheet sh = wb.createSheet("Budget " + b.annee());
        String[] colonnes = {"Mois", "Litres budget", "Litres réel", "CA budget", "CA réel", "Écart CA", "Dépenses budget",
            "Dépenses réel", "Écart dépenses"};
        titre(sh, st, "RÉEL CONTRE BUDGET — " + b.annee() + " (réseau)", colonnes.length);
        Row entete = sh.createRow(2);
        entete.setHeightInPoints(30);
        for (int i = 0; i < colonnes.length; i++) {
            texte(entete, i, colonnes[i], st.entete);
        }
        int ligne = 3;
        for (SuiviMois m : b.mois()) {
            ligneBudget(sh.createRow(ligne++), st, capitale(MOIS_COURT.format(m.mois())), m, false);
        }
        ligneBudget(sh.createRow(ligne), st, "Cumul des mois écoulés", b.cumul(), true);
        sh.setColumnWidth(0, 26 * 256);
        for (int i = 1; i < colonnes.length; i++) {
            sh.setColumnWidth(i, 17 * 256);
        }
        sh.createFreezePane(1, 3);
    }

    private static void ligneBudget(Row r, Styles st, String libelle, SuiviMois m, boolean total) {
        texte(r, 0, libelle, total ? st.totalTexte : st.texte);
        CellStyle litres = total ? st.totalLitres : st.litres;
        CellStyle francs = total ? st.totalFrancs : st.francs;
        nombre(r, 1, m.litresBudget(), litres);
        nombre(r, 2, m.litresReel(), litres);
        nombre(r, 3, m.caBudget(), francs);
        nombre(r, 4, m.caReel(), francs);
        nombre(r, 5, m.caReel().subtract(m.caBudget()), francs);
        nombre(r, 6, m.depensesBudget(), francs);
        nombre(r, 7, m.depensesReel(), francs);
        nombre(r, 8, m.depensesReel().subtract(m.depensesBudget()), francs);
    }

    private static String capitale(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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

    private static final class Styles {
        final CellStyle titre;
        final CellStyle entete;
        final CellStyle texte;
        final CellStyle note;
        final CellStyle litres;
        final CellStyle francs;
        final CellStyle pct;
        final CellStyle totalTexte;
        final CellStyle totalLitres;
        final CellStyle totalFrancs;

        Styles(XSSFWorkbook wb) {
            var gras = wb.createFont();
            gras.setBold(true);
            var grand = wb.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 14);
            var italique = wb.createFont();
            italique.setItalic(true);
            short fmtLitres = wb.createDataFormat().getFormat("#,##0.00");
            short fmtFrancs = wb.createDataFormat().getFormat("#,##0;-#,##0");
            short fmtPct = wb.createDataFormat().getFormat("0.00");
            titre = wb.createCellStyle();
            titre.setFont(grand);
            note = wb.createCellStyle();
            note.setFont(italique);
            entete = bordure(wb.createCellStyle());
            entete.setFont(gras);
            entete.setWrapText(true);
            entete.setAlignment(HorizontalAlignment.CENTER);
            entete.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            texte = bordure(wb.createCellStyle());
            litres = nombre(wb, fmtLitres);
            francs = nombre(wb, fmtFrancs);
            pct = nombre(wb, fmtPct);
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

    byte[] pdf(CompteReseau c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font entete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font note = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

        doc.add(new Paragraph("COMPTE D'EXPLOITATION — " + titreMois(c.mois()).toUpperCase(Locale.FRENCH), titre));
        doc.add(new Paragraph("Montants en F CFA, calculés sur les journées clôturées.", corps));
        doc.add(new Paragraph(" ", corps));
        int nb = c.stations().size() + 2;
        PdfPTable t = new PdfPTable(nb);
        t.setWidthPercentage(100);
        float[] largeurs = new float[nb];
        java.util.Arrays.fill(largeurs, 1f);
        largeurs[0] = 2.2f;
        t.setWidths(largeurs);
        t.setHeaderRows(1);
        cellule(t, "Rubrique", entete, Element.ALIGN_LEFT, true);
        c.stations().forEach(s -> cellule(t, s.stationNom(), entete, Element.ALIGN_CENTER, true));
        cellule(t, "Réseau", entete, Element.ALIGN_CENTER, true);
        for (LigneCompte l : lignes(c)) {
            Font f = l.total() ? gras : corps;
            cellule(t, l.libelle(), f, Element.ALIGN_LEFT, l.total());
            for (CompteStation s : c.stations()) {
                cellule(t, valeur(l, s), f, Element.ALIGN_RIGHT, l.total());
            }
            cellule(t, valeur(l, c.total()), f, Element.ALIGN_RIGHT, l.total());
        }
        cellule(t, "État du mois", corps, Element.ALIGN_LEFT, false);
        c.stations().forEach(s -> cellule(t, etat(s), corps, Element.ALIGN_CENTER, false));
        cellule(t, etat(c.total()), corps, Element.ALIGN_CENTER, false);
        doc.add(t);
        String avertissement = avertissement(c);
        if (avertissement != null) {
            doc.add(new Paragraph(" ", corps));
            doc.add(new Paragraph(avertissement, note));
        }
        doc.close();
        return out.toByteArray();
    }

    private static String valeur(LigneCompte l, CompteStation s) {
        BigDecimal v = l.valeur().apply(s);
        if (v == null) {
            return "—";
        }
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        sym.setGroupingSeparator(' ');
        return new DecimalFormat(l.pourcentage() ? "0.00" : "#,##0", sym).format(v);
    }

    private static void cellule(PdfPTable t, String texte, Font f, int alignement, boolean fond) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, f));
        c.setHorizontalAlignment(alignement);
        c.setPadding(4);
        if (fond) {
            c.setBackgroundColor(new Color(235, 238, 241));
        }
        t.addCell(c);
    }
}
