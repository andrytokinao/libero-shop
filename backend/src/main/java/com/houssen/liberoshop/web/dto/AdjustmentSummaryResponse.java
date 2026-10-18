package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;

/**
 * What every correction adds up to, counted by the database: the screen lists only the latest
 * ones, but its figures cover them all.
 *
 * @param count     corrections recorded, undone ones included
 * @param lostUnits base units lost (breakage, theft, expiry...), undone losses left out
 * @param lostValue those losses at each product's average purchase cost
 */
public record AdjustmentSummaryResponse(long count, BigDecimal lostUnits, BigDecimal lostValue) {
}
