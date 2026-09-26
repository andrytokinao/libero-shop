package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader every import depends on, checked against what spreadsheets actually produce.
 *
 * <p>These are not hypothetical inputs. A French Excel writes semicolons, an English one commas,
 * a file pasted out of a text editor tabs; a product name holding a comma comes back quoted; and
 * an export ends with a blank line. Getting any of those wrong does not fail loudly -- it
 * silently imports one column, or a product called {@code "Riz 5kg;12500;40"}.
 */
class CsvTableTest {

    @Test
    @DisplayName("picks the semicolon a French Excel export uses")
    void detectsSemicolon() {
        CsvTable table = CsvTable.parse("nom;quantite;prix\nRiz 5kg;40;12500\n");

        assertEquals(';', table.separator());
        assertEquals(3, table.headers().size());
        assertEquals(1, table.rows().size());
        assertEquals("Riz 5kg", table.rows().getFirst().cell(0));
        assertEquals("40", table.rows().getFirst().cell(1));
    }

    @Test
    @DisplayName("picks the comma, the tab or the pipe when that is what the header line uses")
    void detectsOtherSeparators() {
        assertEquals(',', CsvTable.parse("nom,quantite,prix\nRiz,40,12500").separator());
        assertEquals('\t', CsvTable.parse("nom\tquantite\tprix\nRiz\t40\t12500").separator());
        assertEquals('|', CsvTable.parse("nom|quantite|prix\nRiz|40|12500").separator());
    }

    @Test
    @DisplayName("keeps a quoted separator inside the cell: a product name may hold a comma")
    void honoursQuotes() {
        CsvTable table = CsvTable.parse("nom,prix\n\"Lait, entier 1L\",4800\n");

        assertEquals("Lait, entier 1L", table.rows().getFirst().cell(0));
        assertEquals("4800", table.rows().getFirst().cell(1));
    }

    @Test
    @DisplayName("a doubled quote inside a quoted cell is one quote")
    void honoursEscapedQuotes() {
        CsvTable table = CsvTable.parse("nom\n\"Tuyau \"\"33 cm\"\"\"\n");

        assertEquals("Tuyau \"33 cm\"", table.rows().getFirst().cell(0));
    }

    @Test
    @DisplayName("a quote in the middle of an unquoted cell stays a quote, not an opening one")
    void quoteMidCellDoesNotOpenAField() {
        CsvTable table = CsvTable.parse("nom;prix\nTuyau 33\";1500\nVis;300\n");

        assertEquals(2, table.rows().size(), "the inch mark must not swallow the next line");
        assertEquals("Tuyau 33\"", table.rows().getFirst().cell(0));
    }

    @Test
    @DisplayName("counts the line a quoted newline spans, so a complaint names the right line")
    void countsLinesAcrossQuotedNewlines() {
        CsvTable table = CsvTable.parse("nom;prix\n\"Riz\nparfume\";12500\nSucre;3200\n");

        assertEquals(2, table.rows().getFirst().line());
        assertEquals("Riz\nparfume", table.rows().getFirst().cell(0));
        assertEquals(4, table.rows().get(1).line(), "the quoted newline must be counted");
    }

    @Test
    @DisplayName("drops blank lines: a spreadsheet export ends with one")
    void dropsBlankLines() {
        CsvTable table = CsvTable.parse("nom;prix\nRiz;12500\n\n\nSucre;3200\n\n");

        assertEquals(2, table.rows().size());
    }

    @Test
    @DisplayName("finds a column by any accepted spelling, whatever its position and accents")
    void findsColumnsByName() {
        CsvTable table = CsvTable.parse("Prix unitaire;Code-Barres;Désignation\n6800;600100;Huile 1L");

        assertEquals(2, table.indexOf("designation", "nom"));
        assertEquals(1, table.indexOf("codebarres"));
        assertEquals(0, table.indexOf("prixunitaire"));
        assertEquals(-1, table.indexOf("categorie"));
    }

    @Test
    @DisplayName("strips the byte-order mark Excel puts in front of a UTF-8 file")
    void stripsBom() {
        CsvTable table = CsvTable.parse("﻿nom;prix\nRiz;12500");

        assertEquals(0, table.indexOf("nom"), "a BOM must not become part of the first header");
    }

    @Test
    @DisplayName("refuses a file with nothing in it, in words the operator can act on")
    void refusesEmptyFile() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> CsvTable.parse("   "));

        assertEquals("IMPORT_EMPTY_FILE", refused.code());
    }

    @Test
    @DisplayName("refuses more rows than one import may carry, and says how many there were")
    void refusesTooManyRows() {
        StringBuilder file = new StringBuilder("nom;prix\n");
        for (int i = 0; i <= CsvTable.MAX_ROWS; i++) {
            file.append("Produit ").append(i).append(";100\n");
        }

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> CsvTable.parse(file.toString()));

        assertEquals("IMPORT_TOO_MANY_ROWS", refused.code());
        assertTrue(refused.getMessage().contains(String.valueOf(CsvTable.MAX_ROWS)));
    }

    @Test
    @DisplayName("a short row answers empty for the columns it does not reach")
    void shortRowsAreNotAnError() {
        CsvTable table = CsvTable.parse("nom;quantite;prix\nRiz\n");

        assertEquals("Riz", table.rows().getFirst().cell(0));
        assertEquals("", table.rows().getFirst().cell(2));
    }

    @Test
    @DisplayName("folds accents, case and punctuation so one header spelling matches many")
    void normalisesForComparison() {
        assertEquals("codebarres", CsvTable.normalise("Code-Barres"));
        assertEquals("codebarres", CsvTable.normalise("CODE_BARRES"));
        assertEquals("quantite", CsvTable.normalise("Quantité"));
        assertEquals("", CsvTable.normalise(null));
    }
}
