package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.CashRemittance;
import com.houssen.libertyshop.entity.RemittanceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashRemittanceResponse(Long id,
                                     BigDecimal amount,
                                     LocalDateTime remittanceDate,
                                     RemittanceStatus status,
                                     UserResponse submittedBy,
                                     UserResponse confirmedBy,
                                     long paymentCount) {

    public static CashRemittanceResponse of(CashRemittance remittance, long paymentCount) {
        return new CashRemittanceResponse(
                remittance.getId(),
                remittance.getAmount(),
                remittance.getRemittanceDate(),
                remittance.getStatus(),
                UserResponse.of(remittance.getSubmittedBy()),
                UserResponse.of(remittance.getConfirmedBy()),
                paymentCount);
    }
}
