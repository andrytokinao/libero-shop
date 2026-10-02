package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentStatus;

import java.math.BigDecimal;

/**
 * A sale was recorded at the counter.
 *
 * <p>Published by {@link SaleService} inside the checkout transaction and meant to be heard
 * after it commits: whoever reacts -- the depot's notifications today, perhaps a printer or a
 * loyalty programme tomorrow -- is added as a listener, and the checkout does not grow a
 * dependency per reaction.
 *
 * <p>Plain values only, copied while the entities are still attached: a listener running after
 * the commit has no session to load a lazy association with.
 *
 * @param units total quantity across the lines, each in its own unit -- what the depot has to
 *              pick, as a rough size of the order rather than a stock figure
 */
public record SaleRecordedEvent(Long invoiceId,
                                String invoiceNumber,
                                String clientName,
                                BigDecimal totalAmount,
                                PaymentStatus paymentStatus,
                                int lineCount,
                                BigDecimal units,
                                Long sellerId,
                                String sellerName) {
}
