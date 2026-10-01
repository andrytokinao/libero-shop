package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.ShopFeatures;
import jakarta.validation.constraints.NotNull;

/**
 * The whole configuration as the screen shows it: the type and every switch, ticked or not.
 * Switches that contradict each other are reconciled by the server, not refused.
 */
public record UpdateShopSettingsRequest(@NotNull BusinessType businessType,
                                        boolean separateDelivery,
                                        boolean payAtDepot,
                                        boolean dualControlRemittance,
                                        boolean cancelAfterDelivery,
                                        boolean orderTakerCollects) {

    public ShopFeatures features() {
        return new ShopFeatures(separateDelivery, payAtDepot, dualControlRemittance, cancelAfterDelivery,
                orderTakerCollects);
    }
}
