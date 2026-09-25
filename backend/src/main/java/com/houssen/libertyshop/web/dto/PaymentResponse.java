package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.Payment;
import com.houssen.libertyshop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param cashRemittanceId null while the collector still physically holds the cash
 */
public record PaymentResponse(Long id,
                              BigDecimal amount,
                              PaymentMethod paymentMethod,
                              LocalDateTime paymentDate,
                              InvoiceRefResponse invoice,
                              UserResponse collectedBy,
                              Long cashRemittanceId) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                InvoiceRefResponse.of(payment.getInvoice()),
                UserResponse.of(payment.getCollectedBy()),
                payment.getCashRemittance() == null ? null : payment.getCashRemittance().getId());
    }
}
