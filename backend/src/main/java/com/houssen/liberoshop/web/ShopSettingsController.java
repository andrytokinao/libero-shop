package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.ShopSettingsService;
import com.houssen.liberoshop.web.dto.ShopSettingsResponse;
import com.houssen.liberoshop.web.dto.UpdateShopSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shop's configuration. Read by every signed-in screen (it also travels with the session),
 * changed by the super-admin only.
 */
@RestController
@RequestMapping("/api/settings")
public class ShopSettingsController {

    private final ShopSettingsService settingsService;

    public ShopSettingsController(ShopSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ShopSettingsResponse current() {
        return settingsService.current();
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ShopSettingsResponse update(@Valid @RequestBody UpdateShopSettingsRequest request) {
        return settingsService.update(request);
    }
}
