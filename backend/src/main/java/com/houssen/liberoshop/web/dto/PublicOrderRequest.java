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
 */
public record PublicOrderRequest(@Size(max = 40) String clientName,
                                 @NotEmpty @Size(max = 30) @Valid List<CreateSaleRequest.Line> lines) {
}
