package com.houssen.liberoshop.service;

import java.util.List;

/**
 * The stock of these products moved: sold, given back by a cancellation, received from a supplier
 * or entered by an import.
 *
 * <p>Published inside the transaction that moved it and heard after the commit, like
 * {@link SaleRecordedEvent}: whoever needs the new figures -- the screens, through
 * {@code StockChangePublisher} -- reads them then. Ids only: the figures are read back once
 * committed, never copied from a transaction that might still roll back.
 */
public record StockChangedEvent(List<Long> productIds) {

    public StockChangedEvent {
        productIds = List.copyOf(productIds);
    }
}
