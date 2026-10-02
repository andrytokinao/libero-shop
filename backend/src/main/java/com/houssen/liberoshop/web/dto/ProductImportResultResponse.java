package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.ImportOutcome;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the import actually did, line by line.
 *
 * <p>Counted rather than trusted: the operator ticked 40 rows expecting 12 creations, and the
 * only honest way to confirm that is to report what the transaction wrote. A line can still be
 * refused here after passing the preview -- somebody else's sale took the barcode in between --
 * and {@code lines} is where that is said.
 *
 * @param rayonsCreated rayons the file named and the import had to create
 * @param movements     stock entries written, one per line that actually raised a quantity --
 *                      so the figure can be checked against the supplies screen
 * @param supplierName  who the entries name as having delivered, or null for an inventory count
 */
public record ProductImportResultResponse(int created,
                                          int merged,
                                          int skipped,
                                          BigDecimal unitsAdded,
                                          int movements,
                                          String supplierName,
                                          List<String> rayonsCreated,
                                          List<Line> lines) {

    /**
     * @param productId the reference created or added to, null when the line was refused
     * @param note      why, in French, for the refused ones; empty for the rest
     */
    public record Line(int line,
                       String name,
                       ImportOutcome outcome,
                       Long productId,
                       String note) {
    }
}
