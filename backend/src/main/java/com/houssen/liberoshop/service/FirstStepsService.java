package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.FirstStepsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The "Premiers pas" of a new shop: what is already in place, read from the data itself rather
 * than from boxes someone ticked -- a product imported from a file counts as much as one typed.
 */
@Service
@Transactional(readOnly = true)
public class FirstStepsService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;
    private final UserAppRepository users;
    private final SaleRepository sales;

    public FirstStepsService(ProductRepository products, ProductPackagingRepository packagings,
                             UserAppRepository users, SaleRepository sales) {
        this.products = products;
        this.packagings = packagings;
        this.users = users;
        this.sales = sales;
    }

    public FirstStepsResponse progress() {
        return new FirstStepsResponse(
                products.count() > 0,
                products.existsByAverageCostIsNotNull(),
                products.existsByBarcodeIsNotNull() || packagings.existsByBarcodeIsNotNull(),
                products.existsByStockQuantityGreaterThan(BigDecimal.ZERO),
                users.countEnabledByRole(RoleApp.CASHIER) > 0,
                sales.count() > 0);
    }
}
