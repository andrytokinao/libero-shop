package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * An order as its customer follows it from the table.
 *
 * @param status RECEIVED (waiting to be served), SERVED, or CANCELLED
 * @param paid   settled, at the table or at the till
 */
public record PublicOrderResponse(String invoiceNumber,
                                  String tableName,
                                  BigDecimal total,
                                  String status,
                                  boolean paid,
                                  List<Line> lines) {

    public record Line(String name, int quantity) {
    }
}
