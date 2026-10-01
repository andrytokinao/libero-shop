package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.ShopFeatures;

/**
 * How the shop works, as every screen needs it: sent with the session, so the menu and the
 * buttons match the settings from the first page on.
 */
public record ShopSettingsResponse(BusinessType businessType,
                                   boolean separateDelivery,
                                   boolean payAtDepot,
                                   boolean dualControlRemittance,
                                   boolean cancelAfterDelivery,
                                   boolean orderTakerCollects) {

    public static ShopSettingsResponse of(BusinessType type, ShopFeatures features) {
        return new ShopSettingsResponse(type, features.separateDelivery(), features.payAtDepot(),
                features.dualControlRemittance(), features.cancelAfterDelivery(),
                features.orderTakerCollects());
    }

    public ShopFeatures features() {
        return new ShopFeatures(separateDelivery, payAtDepot, dualControlRemittance, cancelAfterDelivery,
                orderTakerCollects);
    }
}
