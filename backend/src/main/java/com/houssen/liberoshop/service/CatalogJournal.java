package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.CatalogChange;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CatalogChangeRepository;
import com.houssen.liberoshop.web.dto.CatalogChangeResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The journal of a product's sheet: who changed its price, a unit, its name, and when.
 *
 * <p>Written within the change's own transaction, so a refused change leaves no line, and only
 * when something really changed: saving a sheet as it was writes nothing.
 */
@Service
@Transactional(readOnly = true)
public class CatalogJournal {

    /** How many changes a product's sheet shows. */
    static final int LATEST = 20;

    private final CatalogChangeRepository changes;
    private final ActingUser actingUser;
    private final BusinessCalendar calendar;

    public CatalogJournal(CatalogChangeRepository changes, ActingUser actingUser, BusinessCalendar calendar) {
        this.changes = changes;
        this.actingUser = actingUser;
        this.calendar = calendar;
    }

    /** A change in words; nothing when the two are the same. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Product product, String what, String before, String after) {
        if (Objects.equals(blankToNull(before), blankToNull(after))) {
            return;
        }
        changes.save(CatalogChange.builder()
                .changedAt(calendar.now())
                .changedBy(actingUser.current().orElse(null))
                .product(product)
                .what(what)
                .before(blankToNull(before))
                .after(blankToNull(after))
                .build());
    }

    /** A change of amount or quantity: compared as numbers, so 12500 and 12500.00 are the same. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Product product, String what, BigDecimal before, BigDecimal after) {
        if (before != null && after != null && before.compareTo(after) == 0) {
            return;
        }
        record(product, what, plain(before), plain(after));
    }

    /** A product's latest changes, newest first. */
    public List<CatalogChangeResponse> latestOf(Long productId) {
        return changes.findLatestOf(productId, PageRequest.of(0, LATEST)).stream()
                .map(CatalogChangeResponse::of)
                .toList();
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
