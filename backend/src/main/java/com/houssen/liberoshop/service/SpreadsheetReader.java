package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.util.ExcelUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the sheets of an Excel workbook (.xlsx or .xls) into the same {@link CsvTable}
 * a text file becomes, so the import behind it cannot tell the two apart.
 *
 * <p>Numbers are the one place where going through the cells beats going through what Excel
 * displays. A barcode typed as a number shows as "6,11E+12", and reading the display would
 * import that; the stored value still holds all thirteen digits, and is what is read here.
 * Decimals are written back with a comma, which {@link ProductImportColumns#number} reads as a
 * decimal mark whatever follows it -- a dot followed by three digits would be taken for
 * thousands.
 */
public final class SpreadsheetReader {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SpreadsheetReader() {
    }

    /**
     * The first sheet -- what a one-table import reads.
     *
     * @throws BusinessRuleException when the bytes are not a workbook this can open, or under
     *                               the conditions {@link CsvTable#parse} refuses a file
     */
    public static CsvTable read(InputStream in) {
        return readAll(in).first();
    }

    /**
     * Every sheet, in the workbook's order -- for an import whose second sheet completes the first,
     * like the product import's "Unités".
     *
     * @throws BusinessRuleException as {@link #read}
     */
    public static Sheets readAll(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Map<String, CsvTable> sheets = new LinkedHashMap<>();
            for (Sheet sheet : workbook) {
                sheets.putIfAbsent(sheet.getSheetName(), table(sheet));
            }
            return new Sheets(sheets);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // POI reports a password, a corrupt zip, a Word file renamed .xlsx... each in its
            // own way. None of them is something the operator can act on beyond this sentence.
            throw new BusinessRuleException("IMPORT_UNREADABLE_SPREADSHEET",
                    "Impossible de lire ce fichier Excel. Verifiez qu'il n'est pas protege par un "
                            + "mot de passe, ou enregistrez-le en CSV et recommencez.");
        }
    }

    /**
     * The sheets of a workbook by name, in order.
     *
     * @param byName sheet name as written, to its table
     */
    public record Sheets(Map<String, CsvTable> byName) {

        /** The first sheet, or an empty table for a workbook without any. */
        public CsvTable first() {
            return byName.values().stream().findFirst()
                    .orElseGet(() -> CsvTable.of(List.of(), CsvTable.NO_SEPARATOR));
        }

        /**
         * The sheet named like one of these, compared folded -- "Unités", "unites" and "UNITES"
         * are one name -- or empty. Never the first sheet: that one is the main table.
         *
         * @param names already folded by {@link CsvTable#normalise}
         */
        public Optional<CsvTable> named(Collection<String> names) {
            return byName.entrySet().stream()
                    .skip(1)
                    .filter(entry -> names.contains(CsvTable.normalise(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
    }

    private static CsvTable table(Sheet sheet) {
        List<CsvTable.Row> records = new ArrayList<>();
        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                cells.add(text(row.getCell(i)));
            }
            CsvTable.Row record = new CsvTable.Row(row.getRowNum() + 1, List.copyOf(cells));
            if (!record.isBlank()) {
                records.add(record);
            }
            // Checked while reading, not after: a sheet can claim a million rows.
            if (records.size() > CsvTable.MAX_ROWS + 1) {
                break;
            }
        }
        return CsvTable.of(records, CsvTable.NO_SEPARATOR);
    }

    /** The cell as {@link ExcelUtils#cellValue} reads it, written back as the text a CSV would hold. */
    private static String text(Cell cell) {
        return switch (ExcelUtils.cellValue(cell)) {
            case null -> "";
            // 1.125 as "1,125": with a dot, ProductImportColumns.number would read 1125.
            case Double value -> new BigDecimal(Double.toString(value)).stripTrailingZeros()
                    .toPlainString().replace('.', ',');
            case LocalDateTime date -> date.format(DATE);
            case Object value -> value.toString();
        };
    }
}
