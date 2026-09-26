package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CsvTable;
import com.houssen.liberoshop.service.ProductImportColumns;
import com.houssen.liberoshop.service.ProductImportService;
import com.houssen.liberoshop.web.dto.ProductImportPreviewRequest;
import com.houssen.liberoshop.web.dto.ProductImportPreviewResponse;
import com.houssen.liberoshop.web.dto.ProductImportRequest;
import com.houssen.liberoshop.web.dto.ProductImportResultResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Loading a catalogue from a spreadsheet export, in two calls.
 *
 * <p>{@code /preview} reads and answers, writing nothing; {@code /apply} takes back the lines
 * the operator ticked. Both belong to the roles that own the catalogue -- the depot manager and
 * the super-admin -- because an import is the fastest way there is to change what the shop
 * believes it holds.
 *
 * <p>The preview is a {@code POST} even though it changes nothing: the file's text is the
 * argument, and a catalogue export does not fit in a query string.
 */
@RestController
@RequestMapping("/api/products/import")
@PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
public class ProductImportController {

    private final ProductImportService importService;

    public ProductImportController(ProductImportService importService) {
        this.importService = importService;
    }

    /**
     * What the file says, and what applying it would do to the catalogue. Nothing is written.
     */
    @PostMapping("/preview")
    public ProductImportPreviewResponse preview(@Valid @RequestBody ProductImportPreviewRequest request) {
        return importService.preview(request.content());
    }

    /** Writes the ticked lines. The only call here that touches the catalogue. */
    @PostMapping("/apply")
    public ProductImportResultResponse apply(@Valid @RequestBody ProductImportRequest request) {
        return importService.apply(request);
    }

    /**
     * The columns the import understands, so the dialog can recommend them and offer a model
     * file without repeating the alias table in TypeScript.
     *
     * <p>Served from the enum rather than written out twice: adding a spelling to
     * {@code ProductImportColumns} is then enough for the screen to advertise it.
     */
    @GetMapping("/columns")
    public ImportFormat columns() {
        List<RecommendedColumn> columns = Arrays.stream(ProductImportColumns.Column.values())
                .map(column -> new RecommendedColumn(
                        column.name(),
                        column.label(),
                        column == ProductImportColumns.Column.NAME))
                .toList();
        return new ImportFormat(columns, CsvTable.MAX_ROWS);
    }

    /**
     * @param key      the column's stable identifier, for a UI that wants to label it in its own
     *                 words
     * @param label    the recommended header, which is also the spelling shown in the model file
     * @param required only the product name is; a file with nothing else is a list of names, and
     *                 the preview asks for the rest
     */
    public record RecommendedColumn(String key, String label, boolean required) {
    }

    public record ImportFormat(List<RecommendedColumn> columns, int maxRows) {
    }
}
