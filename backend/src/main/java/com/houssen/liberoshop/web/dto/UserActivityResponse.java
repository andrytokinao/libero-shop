package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;

/** Per-account activity of the day, for the super-admin user list. */
public record UserActivityResponse(UserResponse user,
                                   long salesToday,
                                   BigDecimal collectedToday,
                                   long deliveriesToday,
                                   long remittances) {
}
