package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.CatalogChange;

import java.time.LocalDateTime;

/**
 * One line of a product's journal.
 *
 * @param changedBy who made it; null for a change no one signed in for
 */
public record CatalogChangeResponse(Long id, LocalDateTime changedAt, String changedBy,
                                    String what, String before, String after) {

    public static CatalogChangeResponse of(CatalogChange change) {
        return new CatalogChangeResponse(change.getId(), change.getChangedAt(),
                change.getChangedBy() == null ? null : change.getChangedBy().getFullName(),
                change.getWhat(), change.getBefore(), change.getAfter());
    }
}
