package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.PaymentMethod;

import java.math.BigDecimal;

public record PaymentMethodBreakdownResponse(PaymentMethod paymentMethod, long count, BigDecimal amount) {
}
