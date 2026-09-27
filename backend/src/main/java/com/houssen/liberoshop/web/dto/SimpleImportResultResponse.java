package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.ImportOutcome;

import java.util.List;

/**
 * What a simple import actually did.
 *
 * <p>Counted from what the transaction wrote rather than from what was asked: a line can still be
 * refused here after passing the preview, because somebody else created the same rayon in
 * between, and {@code lines} is where that is said rather than swallowed.
 */
public record SimpleImportResultResponse(int created,
                                         int merged,
                                         int skipped,
                                         List<Line> lines) {

    /** @param note why, in French, for anything the operator would otherwise have to guess at */
    public record Line(int line, String label, ImportOutcome outcome, Long id, String note) {
    }
}
