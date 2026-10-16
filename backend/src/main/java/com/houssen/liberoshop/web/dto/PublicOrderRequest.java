package com.houssen.liberoshop.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An order sent from a table's QR code. The table is in the link, never in the payload, and the
 * prices are read on the server: nothing here can name another table or set a price.
 *
 * @param clientName optional: the customer's name, to call them when it is served
 * @param clientReference optional: drawn by the phone for this order and sent again on a retry, so
 *                        that an order whose answer was lost is not recorded twice
 */
public record PublicOrderRequest(@Size(max = 40) String clientName,
                                 @NotEmpty @Size(max = 30) @Valid List<CreateSaleRequest.Line> lines,
                                 @Size(max = MAX_REFERENCE_LENGTH) String clientReference) {

    /** Leaves room in the sale's reference column for the table it is tied to. */
    public static final int MAX_REFERENCE_LENGTH = 40;

    /** An order without a reference: what every phone posted before there was one. */
    public PublicOrderRequest(String clientName, List<CreateSaleRequest.Line> lines) {
        this(clientName, lines, null);
    }
}
