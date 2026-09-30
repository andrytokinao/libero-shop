package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.CashRemittance;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.RemittanceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param invoices the orders whose cash the slip carries, so the cashier can count against them
 *                 before confirming
 */
public record CashRemittanceResponse(Long id,
                                     BigDecimal amount,
                                     LocalDateTime remittanceDate,
                                     RemittanceStatus status,
                                     UserResponse submittedBy,
                                     UserResponse confirmedBy,
                                     long paymentCount,
                                     List<RemittedInvoice> invoices) {

    public static CashRemittanceResponse of(CashRemittance remittance, List<Payment> payments) {
        return new CashRemittanceResponse(
                remittance.getId(),
                remittance.getAmount(),
                remittance.getRemittanceDate(),
                remittance.getStatus(),
                UserResponse.of(remittance.getSubmittedBy()),
                UserResponse.of(remittance.getConfirmedBy()),
                payments.size(),
                payments.stream().map(RemittedInvoice::of).toList());
    }

    /** One order in a slip, and the cash collected for it. */
    public record RemittedInvoice(Long invoiceId, String invoiceNumber, String clientName, BigDecimal amount) {

        static RemittedInvoice of(Payment payment) {
            return new RemittedInvoice(payment.getInvoice().getId(), payment.getInvoice().getInvoiceNumber(),
                    payment.getInvoice().getClientName(), payment.getAmount());
        }
    }
}
