package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.StockOutput;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An OUTPUT row of the stock_movement table: goods leaving against an invoice. */
public record StockOutputResponse(Long id,
                                  int quantity,
                                  LocalDateTime movementDate,
                                  ProductResponse product,
                                  UserResponse performedBy,
                                  InvoiceRefResponse invoice,
                                  BigDecimal value) {

    public static StockOutputResponse of(StockOutput output) {
        return new StockOutputResponse(
                output.getId(),
                output.getQuantity(),
                output.getMovementDate(),
                ProductResponse.of(output.getProduct()),
                UserResponse.of(output.getPerformedBy()),
                InvoiceRefResponse.of(output.getInvoice()),
                output.getProduct().getPrice().multiply(BigDecimal.valueOf(output.getQuantity())));
    }
}
