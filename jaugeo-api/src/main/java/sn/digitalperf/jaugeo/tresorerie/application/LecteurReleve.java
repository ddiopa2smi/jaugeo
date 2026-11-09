package sn.digitalperf.jaugeo.tresorerie.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import sn.digitalperf.jaugeo.commun.ErreurMetier;
import sn.digitalperf.jaugeo.tresorerie.domaine.FormatReleve;

/**
 * Lecture d'un releve CSV ou Excel selon son format : seules les operations au credit sont retenues
 * (versements, paiements recus) ; les lignes sans credit (debits, soldes, totaux) sont ignorees.
 */
public final class LecteurReleve {

    public record Operation(int ligne, LocalDate date, String libelle, String reference, BigDecimal montant) {
    }

    public record Lecture(List<Operation> operations, int ignorees) {
    }

    private LecteurReleve() {
    }

    public static Lecture lire(byte[] contenu, String nomFichier, FormatReleve f) {
        List<List<String>> lignes = classeur(contenu) ? lignesExcel(contenu) : lignesCsv(texte(contenu), f.getSeparateur().charAt(0));
        DateTimeFormatter format = DateTimeFormatter.ofPattern(f.getFormatDate(), Locale.FRENCH);
        List<Operation> operations = new ArrayList<>();
        int ignorees = 0;
        for (int i = f.getLignesIgnorees(); i < lignes.size(); i++) {
            List<String> l = lignes.get(i);
            int numero = i + 1;
            if (l.stream().allMatch(String::isBlank)) {
                continue;
            }
            BigDecimal montant = montant(cellule(l, f.getColCredit()), numero);
            if (montant == null || montant.signum() <= 0) {
                ignorees++;
                continue;
            }
            String brute = cellule(l, f.getColDate());
            LocalDate date = date(brute, format);
            if (date == null) {
                throw ErreurMetier.regle("RELEVE_ILLISIBLE", "Ligne " + numero + " : date « " + brute
                        + " » illisible (format attendu " + f.getFormatDate() + "). Vérifiez le format du relevé.");
            }
            String libelle = tronquer(cellule(l, f.getColLibelle()), 300);
            String reference = f.getColReference() == null ? null : tronquer(cellule(l, f.getColReference()), 80);
            operations.add(new Operation(numero, date, libelle.isEmpty() ? "(sans libellé)" : libelle,
                    reference == null || reference.isEmpty() ? null : reference, montant.setScale(0, RoundingMode.HALF_UP)));
        }
        return new Lecture(operations, ignorees);
    }

    // ---------------------------------------------------------------- Montants et dates

    /** Montant ecrit a la francaise ou a l'anglaise : « 1 250 000 », « 1.250.000,00 », « 1,250,000.00 », « (500) ». */
    static BigDecimal montant(String brut, int ligne) {
        String s = brut.replaceAll("[\\s\\u00A0\\u202F]", "").replaceAll("(?i)(FCFA|XOF|CFA|F)$", "");
        if (s.isEmpty() || s.equals("-")) {
            return null;
        }
        boolean negatif = s.startsWith("-") || s.endsWith("-") || (s.startsWith("(") && s.endsWith(")"));
        s = s.replaceAll("[()+-]", "");
        int virgule = s.lastIndexOf(',');
        int point = s.lastIndexOf('.');
        if (virgule >= 0 && point >= 0) {
            char decimal = virgule > point ? ',' : '.';
            s = s.replace(decimal == ',' ? "." : ",", "").replace(decimal, '.');
        } else if (virgule >= 0 || point >= 0) {
            char sep = virgule >= 0 ? ',' : '.';
            // Separateur de milliers seul (1,250,000) ou separateur decimal (1250,50)
            if (s.matches("\\d{1,3}(\\" + sep + "\\d{3})+")) {
                s = s.replace(String.valueOf(sep), "");
            } else {
                s = s.replace(sep, '.');
            }
        }
        try {
            BigDecimal m = new BigDecimal(s);
            return negatif ? m.negate() : m;
        } catch (NumberFormatException e) {
            throw ErreurMetier.regle("RELEVE_ILLISIBLE", "Ligne " + ligne + " : montant « " + brut
                    + " » illisible. Vérifiez la colonne du crédit dans le format du relevé.");
        }
    }

    static LocalDate date(String brute, DateTimeFormatter format) {
        String s = brute.trim();
        for (String essai : new String[] {s, s.split("[\\sT]")[0]}) {
            try {
                return LocalDate.parse(essai, format);
            } catch (DateTimeParseException e) {
                // essai suivant
            }
            try {
                return LocalDate.parse(essai);
            } catch (DateTimeParseException e) {
                // essai suivant
            }
        }
        return null;
    }

    private static String cellule(List<String> l, int colonne) {
        return colonne <= l.size() ? l.get(colonne - 1).trim() : "";
    }

    private static String tronquer(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ---------------------------------------------------------------- CSV

    private static boolean classeur(byte[] o) {
        // Signature ZIP (xlsx) ou OLE2 (xls)
        return o.length > 4 && ((o[0] == 'P' && o[1] == 'K') || (o[0] == (byte) 0xD0 && o[1] == (byte) 0xCF));
    }

    /** UTF-8 si le fichier en est, sinon Windows-1252 (exports Excel francais). */
    static String texte(byte[] o) {
        String s;
        try {
            s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(o)).toString();
        } catch (CharacterCodingException e) {
            s = new String(o, Charset.forName("windows-1252"));
        }
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Decoupage CSV : guillemets doubles, separateurs et retours a la ligne entre guillemets. */
    static List<List<String>> lignesCsv(String texte, char separateur) {
        List<List<String>> lignes = new ArrayList<>();
        List<String> courante = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (guillemets) {
                if (c == '"' && i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                    champ.append('"');
                    i++;
                } else if (c == '"') {
                    guillemets = false;
                } else {
                    champ.append(c);
                }
            } else if (c == '"') {
                guillemets = true;
            } else if (c == separateur) {
                courante.add(champ.toString());
                champ.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') {
                    i++;
                }
                courante.add(champ.toString());
                champ.setLength(0);
                lignes.add(courante);
                courante = new ArrayList<>();
            } else {
                champ.append(c);
            }
        }
        if (!champ.isEmpty() || !courante.isEmpty()) {
            courante.add(champ.toString());
            lignes.add(courante);
        }
        return lignes;
    }

    // ---------------------------------------------------------------- Excel

    /** Premiere feuille du classeur ; les dates Excel sont rendues au format ISO. */
    private static List<List<String>> lignesExcel(byte[] contenu) {
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet feuille = classeur.getSheetAt(0);
            DataFormatter formateur = new DataFormatter(Locale.FRENCH);
            List<List<String>> lignes = new ArrayList<>();
            for (int r = 0; r <= feuille.getLastRowNum(); r++) {
                Row ligne = feuille.getRow(r);
                List<String> valeurs = new ArrayList<>();
                if (ligne != null) {
                    for (int c = 0; c < Math.max(ligne.getLastCellNum(), 0); c++) {
                        valeurs.add(valeur(ligne.getCell(c), formateur));
                    }
                }
                lignes.add(valeurs);
            }
            return lignes;
        } catch (IOException | RuntimeException e) {
            throw ErreurMetier.regle("RELEVE_ILLISIBLE", "Le classeur Excel n'a pas pu être lu.");
        }
    }

    private static String valeur(Cell c, DataFormatter formateur) {
        if (c == null) {
            return "";
        }
        CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            return BigDecimal.valueOf(c.getNumericCellValue()).toPlainString();
        }
        return formateur.formatCellValue(c);
    }
}
