package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.ShopFeatures;
import com.houssen.liberoshop.entity.ShopSettings;
import com.houssen.liberoshop.repository.ShopSettingsRepository;
import com.houssen.liberoshop.web.dto.ShopSettingsResponse;
import com.houssen.liberoshop.web.dto.UpdateShopSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How this shop works: the one place the sale, hand-over and remittance rules ask.
 *
 * <p>Until a super-admin saves a configuration there is no row, and the answer is
 * {@link #DEFAULT_TYPE} -- the way the application worked before it could be configured, so an
 * existing installation keeps its behaviour after the upgrade without a migration.
 */
@Service
@Transactional(readOnly = true)
public class ShopSettingsService {

    static final long SINGLETON_ID = 1L;
    public static final BusinessType DEFAULT_TYPE = BusinessType.WHOLESALE_DEPOT;

    private final ShopSettingsRepository repository;

    public ShopSettingsService(ShopSettingsRepository repository) {
        this.repository = repository;
    }

    public ShopSettingsResponse current() {
        return repository.findById(SINGLETON_ID)
                .map(settings -> ShopSettingsResponse.of(settings.getBusinessType(), settings.features()))
                .orElseGet(() -> ShopSettingsResponse.of(DEFAULT_TYPE, DEFAULT_TYPE.defaults()));
    }

    public ShopFeatures features() {
        return current().features();
    }

    /** Saves the whole configuration; contradictory switches are reconciled, see {@link ShopFeatures#normalized()}. */
    @Transactional
    public ShopSettingsResponse update(UpdateShopSettingsRequest request) {
        ShopFeatures features = request.features().normalized();
        ShopSettings settings = repository.findById(SINGLETON_ID)
                .orElseGet(() -> ShopSettings.builder().id(SINGLETON_ID).build());
        settings.setBusinessType(request.businessType());
        settings.setSeparateDelivery(features.separateDelivery());
        settings.setPayAtDepot(features.payAtDepot());
        settings.setDualControlRemittance(features.dualControlRemittance());
        settings.setCancelAfterDelivery(features.cancelAfterDelivery());
        settings.setOrderTakerCollects(features.orderTakerCollects());
        settings.setOnlineOrdering(features.onlineOrdering());
        repository.save(settings);
        return ShopSettingsResponse.of(settings.getBusinessType(), features);
    }
}
