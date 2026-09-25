package com.houssen.libertyshop.web.dto;

import java.math.BigDecimal;

public record CategoryStockResponse(CategoryResponse category,
                                    long references,
                                    long units,
                                    BigDecimal value,
                                    int sharePercent) {
}
