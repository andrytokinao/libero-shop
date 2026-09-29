package com.houssen.liberoshop.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelUtilsTest {

    @Test
    @DisplayName("writes and reads back every sheet, column order and value type")
    void roundTrip(@TempDir Path dir) {
        Map<String, List<Map<String, Object>>> sheets = new LinkedHashMap<>();
        sheets.put("Produits", List.of(
                row("Nom", "Riz", "Quantité", 50, "Prix", 4500.5, "Code", "6111234567890"),
                row("Nom", "Sucre", "Quantité", null, "Prix", 3200, "Code", null)));
        sheets.put("Dates", List.of(
                row("Jour", LocalDate.of(2026, 9, 29), "Actif", true)));

        String path = dir.resolve("export.xlsx").toString();
        ExcelUtils.write(sheets, path);
        Map<String, List<Map<String, Object>>> read = ExcelUtils.read(path);

        assertEquals(List.of("Produits", "Dates"), List.copyOf(read.keySet()));
        Map<String, Object> rice = read.get("Produits").getFirst();
        assertEquals(List.of("Nom", "Quantité", "Prix", "Code"), List.copyOf(rice.keySet()));
        assertEquals("Riz", rice.get("Nom"));
        assertEquals(50L, rice.get("Quantité"));
        assertEquals(4500.5, rice.get("Prix"));
        assertEquals("6111234567890", rice.get("Code"));
        assertNull(read.get("Produits").get(1).get("Quantité"));
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), read.get("Dates").getFirst().get("Jour"));
        assertEquals(true, read.get("Dates").getFirst().get("Actif"));
    }

    @Test
    @DisplayName("takes the first non-blank row as header, names empty and repeated headers, skips blank rows")
    void headers() throws IOException {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Feuil1");
            Row head = sheet.createRow(2);
            head.createCell(0).setCellValue("Nom");
            head.createCell(2).setCellValue("Nom");
            Row line = sheet.createRow(3);
            line.createCell(0).setCellValue("Riz");
            line.createCell(1).setCellValue(6111234567890d);
            line.createCell(2).setCellValue("bis");
            sheet.createRow(6).createCell(0).setCellValue("Sucre");
            workbook.write(out);
            file = out.toByteArray();
        }

        List<Map<String, Object>> rows = ExcelUtils.read(file).get("Feuil1");

        assertEquals(2, rows.size());
        assertEquals(List.of("Nom", "B", "Nom (2)"), List.copyOf(rows.getFirst().keySet()));
        assertEquals(6111234567890L, rows.getFirst().get("B"));
        assertEquals("Sucre", rows.get(1).get("Nom"));
        assertNull(rows.get(1).get("Nom (2)"));
    }

    @Test
    @DisplayName("puts the columns named in the order map first, by their number, and the rest after")
    void columnOrder() {
        Map<String, List<Map<String, Object>>> sheets = Map.of("Produits", List.of(
                row("Code", "611", "Prix", 4500, "Nom", "Riz", "Unité", "sac")));
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put("Nom", 0);
        order.put("Prix", 1);
        order.put("Absente", 2);

        Map<String, Object> read = ExcelUtils.read(ExcelUtils.toBytes(sheets, order)).get("Produits").getFirst();
        Map<String, Object> unordered = ExcelUtils.read(ExcelUtils.toBytes(sheets)).get("Produits").getFirst();

        assertEquals(List.of("Nom", "Prix", "Code", "Unité"), new ArrayList<>(read.keySet()));
        assertEquals(List.of("Code", "Prix", "Nom", "Unité"), new ArrayList<>(unordered.keySet()));
    }

    @Test
    @DisplayName("refuses bytes that are not a workbook")
    void notAWorkbook() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelUtils.read("nom;prix".getBytes(StandardCharsets.UTF_8)));
    }

    /** A row from alternating keys and values, nulls allowed (Map.of refuses them). */
    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }
}
