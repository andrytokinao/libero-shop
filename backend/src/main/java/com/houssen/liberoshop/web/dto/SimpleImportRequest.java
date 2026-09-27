package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.ImportAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** The lines the operator ticked in a simple import, as they left them. */
public record SimpleImportRequest(@NotEmpty @Valid List<Line> lines) {

    /**
     * @param values      keyed by the field specs the preview sent back; re-sent rather than
     *                    looked up, because every one of them is editable in the dialog
     * @param action      {@code CREATE} to add, {@code MERGE} to complete {@code mergeIntoId}
     * @param mergeIntoId required by {@code MERGE}, ignored otherwise
     */
    public record Line(int line,
                       @NotNull Map<String, String> values,
                       @NotNull ImportAction action,
                       Long mergeIntoId) {
    }
}
