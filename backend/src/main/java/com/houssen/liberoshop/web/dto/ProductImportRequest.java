package com.houssen.liberoshop.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The lines the operator ticked in the preview, ready to be applied.
 *
 * <p>Only the ticked ones: the browser drops the rest, so a file of 500 lines of which 12 were
 * wanted sends 12. The untouched lines are not "refused" anywhere, they simply never arrive.
 *
 * @param lines at least one -- an empty apply is a mistake worth naming rather than a no-op
 *              that looks like success
 */
public record ProductImportRequest(@NotEmpty @Valid List<ProductImportLineRequest> lines) {
}
