package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.ShopInfo;
import com.houssen.liberoshop.repository.ShopInfoRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shop's name, address and contact, as the invoices and receipts print them.
 *
 * <p>While nothing has been saved, {@link #current()} answers with the {@code liberoshop.shop-*}
 * properties, so a shop that never opened the settings screen prints what it printed before.
 * Nothing is written by reading.
 */
@Service
@Transactional(readOnly = true)
public class ShopInfoService {

    private final ShopInfoRepository shops;
    private final String defaultName;
    private final String defaultAddress;
    private final String defaultContact;

    public ShopInfoService(ShopInfoRepository shops,
                           @Value("${liberoshop.shop-name:Moussa Shop}") String defaultName,
                           @Value("${liberoshop.shop-address:}") String defaultAddress,
                           @Value("${liberoshop.shop-contact:}") String defaultContact) {
        this.shops = shops;
        this.defaultName = defaultName;
        this.defaultAddress = defaultAddress;
        this.defaultContact = defaultContact;
    }

    /** What the shop saved, or else what the properties say. */
    public ShopInfo current() {
        return shops.findById(ShopInfo.SINGLETON_ID).orElseGet(() -> ShopInfo.builder()
                .name(defaultName)
                .address(defaultAddress)
                .contact(defaultContact)
                .build());
    }

    /**
     * Saves the shop's identity, creating the row the first time.
     *
     * @param address null or blank to print no address
     * @param contact null or blank to print no contact
     * @throws BusinessRuleException when the name is blank
     */
    @Transactional
    public ShopInfo update(String name, String address, String contact) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("SHOP_NAME_REQUIRED", "Le nom de la boutique est obligatoire.");
        }
        ShopInfo info = shops.findById(ShopInfo.SINGLETON_ID)
                .orElseGet(() -> ShopInfo.builder().build());
        info.setName(name.trim());
        info.setAddress(blankToNull(address));
        info.setContact(blankToNull(contact));
        return shops.save(info);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}