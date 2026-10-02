package com.houssen.liberoshop.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * A storekeeper brought cash to the desk ({@code confirmed = false}), or a cashier confirmed
 * receiving it ({@code confirmed = true}).
 *
 * <p>Published by {@link RemittanceService} inside the transaction and meant to be heard after it
 * commits, like {@link SaleRecordedEvent}. Plain values only, for the same reason.
 *
 * @param invoiceIds  the same orders, for the listeners that read them back
 * @param cashierName who confirmed; null while the slip is pending
 */
public record RemittanceRecordedEvent(Long remittanceId,
                                      BigDecimal amount,
                                      List<String> invoiceNumbers,
                                      List<Long> invoiceIds,
                                      Long agentId,
                                      String agentName,
                                      boolean confirmed,
                                      Long cashierId,
                                      String cashierName) {
}
