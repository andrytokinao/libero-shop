package com.houssen.liberoshop.entity;

/** Why the stock was corrected by hand, picked from a short list so the losses can be counted. */
public enum AdjustmentReason {
    /** Broken, torn, spilled: goods that can no longer be sold. */
    BREAKAGE,
    /** Missing with nothing to explain it. */
    THEFT,
    /** Past its date, or spoiled. */
    EXPIRED,
    /** The shelf was counted, and the count is what the stock becomes -- up or down. */
    INVENTORY_COUNT,
    /** Any other loss; the comment then says what. */
    OTHER;

    /** Whether the quantity given is what was counted rather than what was lost. */
    public boolean isCount() {
        return this == INVENTORY_COUNT;
    }
}
