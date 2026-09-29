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
import java.util.List;

/**
 * Reads the first sheet of an Excel workbook (.xlsx or .xls) into the same {@link CsvTable}
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
     * @throws BusinessRuleException when the bytes are not a workbook this can open, or under
     *                               the conditions {@link CsvTable#parse} refuses a file
     */
    public static CsvTable read(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return CsvTable.of(List.of(), CsvTable.NO_SEPARATOR);
            }
            Sheet sheet = workbook.getSheetAt(0);
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
