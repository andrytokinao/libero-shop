package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.ProductImportColumns.Column;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Excel side of the import, and the model file that feeds it.
 */
class SpreadsheetReaderTest {

    @Test
    @DisplayName("reads the model file back with every column recognised and its examples intact")
    void modelRoundTrips() {
        CsvTable table = SpreadsheetReader.read(new ByteArrayInputStream(ProductImportTemplate.xlsx()));
        ProductImportColumns.Mapping mapping = ProductImportColumns.map(table);

        assertTrue(mapping.missing().isEmpty(), "missing: " + mapping.missing());
        assertTrue(mapping.ignored().isEmpty(), "ignored: " + mapping.ignored());
        assertEquals(CsvTable.NO_SEPARATOR, table.separator());
        assertEquals(2, table.rows().size());

        CsvTable.Row rice = table.rows().getFirst();
        assertEquals(2, rice.line());
        assertEquals("Riz Makalioka 1kg", mapping.cell(rice, Column.NAME));
        assertEquals("50", mapping.cell(rice, Column.QUANTITY));
        assertEquals("4500", mapping.cell(rice, Column.PRICE));
        assertEquals("6111234567890", mapping.cell(rice, Column.BARCODE));
        assertEquals("Épicerie > Riz", mapping.cell(rice, Column.CATEGORY));
    }

    @Test
    @DisplayName("keeps every digit of a barcode typed as a number, and reads decimals right")
    void numbersFromTheStoredValue() throws IOException {
        byte[] file = workbook(sheet -> {
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("Désignation");
            head.createCell(1).setCellValue("EAN");
            head.createCell(2).setCellValue("Qte");
            Row line = sheet.createRow(1);
            line.createCell(0).setCellValue("Sucre");
            line.createCell(1).setCellValue(6111234567890d);
            line.createCell(2).setCellValue(1.125);
        });

        CsvTable table = SpreadsheetReader.read(new ByteArrayInputStream(file));
        ProductImportColumns.Mapping mapping = ProductImportColumns.map(table);
        CsvTable.Row sugar = table.rows().getFirst();

        assertEquals("6111234567890", mapping.cell(sugar, Column.BARCODE));
        // "1.125" would be read as eleven hundred; the comma keeps it a decimal.
        assertEquals(new BigDecimal("1.125"),
                ProductImportColumns.number(mapping.cell(sugar, Column.QUANTITY)));
    }

    @Test
    @DisplayName("skips blank rows and still numbers lines as Excel shows them")
    void blankRows() throws IOException {
        byte[] file = workbook(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("nom");
            sheet.createRow(1).createCell(0).setCellValue("Riz");
            sheet.createRow(4).createCell(0).setCellValue("Sucre");
        });

        CsvTable table = SpreadsheetReader.read(new ByteArrayInputStream(file));

        assertEquals(2, table.rows().size());
        assertEquals(5, table.rows().get(1).line());
    }

    @Test
    @DisplayName("refuses something that is not a workbook with a sentence, not a stack trace")
    void notAWorkbook() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> SpreadsheetReader.read(new ByteArrayInputStream(
                        "nom;prix\nRiz;12500".getBytes(StandardCharsets.UTF_8))));

        assertTrue(refused.getMessage().contains("Excel"));
    }

    private interface SheetFiller {
        void fill(Sheet sheet);
    }

    private static byte[] workbook(SheetFiller filler) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filler.fill(workbook.createSheet());
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
