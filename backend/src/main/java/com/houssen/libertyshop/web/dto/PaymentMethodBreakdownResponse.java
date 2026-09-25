package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.PaymentMethod;

import java.math.BigDecimal;

public record PaymentMethodBreakdownResponse(PaymentMethod paymentMethod, long count, BigDecimal amount) {
}
