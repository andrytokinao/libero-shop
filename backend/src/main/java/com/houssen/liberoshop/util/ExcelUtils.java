package com.houssen.liberoshop.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes Excel workbooks as plain data:
 * {@code Map<sheet name, List<Map<column name, value>>>}.
 *
 * <p>On each sheet the first non-blank row is the header; every row below it becomes one map
 * keyed by those headers, in column order. Blank rows are left out. Maps are
 * {@link LinkedHashMap}s, so sheets and columns come back in the order the workbook has them,
 * and are written in the order the caller put them.
 *
 * <p>Values read are {@code String}, {@code Long} (a whole number -- a barcode keeps all its
 * digits), {@code Double}, {@code Boolean}, {@code LocalDateTime} (a date-formatted cell), or
 * {@code null} for an empty cell. A formula gives its last computed result.
 */
public final class ExcelUtils {

    private ExcelUtils() {
    }

    // ------------------------------------------------------------------ reading

    /** @param path the workbook's path on disk, .xlsx or .xls */
    public static Map<String, List<Map<String, Object>>> read(String path) {
        return read(Path.of(path));
    }

    public static Map<String, List<Map<String, Object>>> read(File file) {
        return read(file.toPath());
    }

    public static Map<String, List<Map<String, Object>>> read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, List<Map<String, Object>>> read(byte[] content) {
        return read(new ByteArrayInputStream(content));
    }

    /**
     * Reads every sheet. The stream is not closed.
     *
     * @throws IllegalArgumentException when the bytes are not a workbook POI can open
     */
    public static Map<String, List<Map<String, Object>>> read(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Map<String, List<Map<String, Object>>> sheets = new LinkedHashMap<>();
            for (Sheet sheet : workbook) {
                sheets.put(sheet.getSheetName(), readSheet(sheet));
            }
            return sheets;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Not a readable Excel workbook", e);
        }
    }

    private static List<Map<String, Object>> readSheet(Sheet sheet) {
        List<String> headers = null;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                values.add(cellValue(row.getCell(i)));
            }
            if (values.stream().allMatch(ExcelUtils::isBlank)) {
                continue;
            }
            if (headers == null) {
                headers = headers(values);
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                line.put(headers.get(i), i < values.size() ? values.get(i) : null);
            }
            rows.add(line);
        }
        return rows;
    }

    /**
     * Header cells as keys. An empty header is named by its column letter ("C"), and a repeated
     * one gets " (2)", " (3)"... -- two columns may not share a key.
     */
    private static List<String> headers(List<Object> cells) {
        List<String> headers = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            Object cell = cells.get(i);
            String base = isBlank(cell) ? CellReference.convertNumToColString(i) : cell.toString().trim();
            String name = base;
            for (int n = 2; !taken.add(name); n++) {
                name = base + " (" + n + ")";
            }
            headers.add(name);
        }
        return headers;
    }

    /** One cell as a Java value, by the rules in the class comment. */
    public static Object cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> {
                String text = cell.getStringCellValue();
                yield text.isEmpty() ? null : text;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue();
                }
                double value = cell.getNumericCellValue();
                yield value == Math.rint(value) && Math.abs(value) < 1e15 ? (Object) (long) value : (Object) value;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> null;
        };
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    // ------------------------------------------------------------------ writing

    /*
     * Every write takes an optional columnOrder: column name -> its number, e.g.
     * order.put("Nom produit", 0). Columns it names come first, sorted by that number; the others
     * follow in the order first met. It applies to every sheet, and a name a sheet does not
     * have is ignored. Without it (or with null), columns are in the order first met.
     */

    /** Writes an .xlsx to that path, replacing it. */
    public static void write(Map<String, List<Map<String, Object>>> sheets, String path) {
        write(sheets, path, null);
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, String path,
                             Map<String, Integer> columnOrder) {
        write(sheets, Path.of(path), columnOrder);
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, File file) {
        write(sheets, file, null);
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, File file,
                             Map<String, Integer> columnOrder) {
        write(sheets, file.toPath(), columnOrder);
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, Path path) {
        write(sheets, path, null);
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, Path path,
                             Map<String, Integer> columnOrder) {
        try (OutputStream out = Files.newOutputStream(path)) {
            write(sheets, out, columnOrder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] toBytes(Map<String, List<Map<String, Object>>> sheets) {
        return toBytes(sheets, null);
    }

    public static byte[] toBytes(Map<String, List<Map<String, Object>>> sheets,
                                 Map<String, Integer> columnOrder) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(sheets, out, columnOrder);
        return out.toByteArray();
    }

    public static void write(Map<String, List<Map<String, Object>>> sheets, OutputStream out) {
        write(sheets, out, null);
    }

    /**
     * Writes an .xlsx, one sheet per entry. The stream is not closed.
     *
     * <p>The header row is bold and frozen, and holds every key met in the sheet's rows, ordered
     * by {@code columnOrder} as described above. A column whose values are all text is formatted
     * as text, including for cells typed into it later: a barcode typed there stays thirteen
     * digits instead of turning into "6,11E+12".
     *
     * @param columnOrder column name to its number; optional, may be null
     */
    public static void write(Map<String, List<Map<String, Object>>> sheets, OutputStream out,
                             Map<String, Integer> columnOrder) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            for (Map.Entry<String, List<Map<String, Object>>> entry : sheets.entrySet()) {
                writeSheet(workbook.createSheet(WorkbookUtil.createSafeSheetName(entry.getKey())),
                        entry.getValue(), columnOrder, styles);
            }
            workbook.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The sheet's columns: those {@code columnOrder} names by their number, then the rest as met. */
    private static List<String> columns(List<Map<String, Object>> rows, Map<String, Integer> columnOrder) {
        Set<String> keys = new LinkedHashSet<>();
        rows.forEach(row -> keys.addAll(row.keySet()));
        if (columnOrder == null || columnOrder.isEmpty()) {
            return List.copyOf(keys);
        }
        List<String> ordered = new ArrayList<>(keys.stream().filter(columnOrder::containsKey)
                .sorted(Comparator.comparing(columnOrder::get)).toList());
        keys.stream().filter(key -> !columnOrder.containsKey(key)).forEach(ordered::add);
        return ordered;
    }

    private static void writeSheet(Sheet sheet, List<Map<String, Object>> rows,
                                   Map<String, Integer> columnOrder, Styles styles) {
        List<String> columns = columns(rows, columnOrder);

        Row head = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(columns.get(i));
            cell.setCellStyle(styles.header);
        }

        int[] widths = columns.stream().mapToInt(String::length).toArray();
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            for (int i = 0; i < columns.size(); i++) {
                Object value = rows.get(r).get(columns.get(i));
                if (value != null) {
                    setValue(row.createCell(i), value, styles);
                    widths[i] = Math.max(widths[i], value.toString().length());
                }
            }
        }

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            boolean allText = rows.stream().map(row -> row.get(column)).filter(v -> v != null)
                    .allMatch(v -> v instanceof CharSequence);
            if (allText) {
                sheet.setDefaultColumnStyle(i, styles.text);
                for (int r = 0; r < rows.size(); r++) {
                    Cell cell = sheet.getRow(r + 1).getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(styles.text);
                    }
                }
            }
            sheet.setColumnWidth(i, Math.min(Math.max(widths[i] + 2, 10), 100) * 256);
        }
        if (!columns.isEmpty()) {
            sheet.createFreezePane(0, 1);
        }
    }

    private static void setValue(Cell cell, Object value, Styles styles) {
        switch (value) {
            case Number number -> cell.setCellValue(number instanceof BigDecimal decimal
                    ? decimal.doubleValue() : number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                cell.setCellStyle(styles.dateTime);
            }
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(styles.date);
            }
            case Date date -> {
                cell.setCellValue(date);
                cell.setCellStyle(styles.dateTime);
            }
            default -> cell.setCellValue(value.toString());
        }
    }

    /** Created once per workbook: Excel caps the number of distinct styles a file may hold. */
    private static final class Styles {
        final CellStyle header;
        final CellStyle text;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);
            text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
        }
    }
}
