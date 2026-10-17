package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.FirstStepsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The "Premiers pas" read from what the shop holds, never from boxes ticked by hand. */
class FirstStepsServiceTest {

    private ProductRepository products;
    private ProductPackagingRepository packagings;
    private UserAppRepository users;
    private SaleRepository sales;
    private FirstStepsService firstSteps;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        packagings = mock(ProductPackagingRepository.class);
        users = mock(UserAppRepository.class);
        sales = mock(SaleRepository.class);
        firstSteps = new FirstStepsService(products, packagings, users, sales);
    }

    @Test
    @DisplayName("a new shop: nothing done yet")
    void boutiqueNeuveRienDeFait() {
        assertEquals(new FirstStepsResponse(false, false, false, false, false, false), firstSteps.progress());
    }

    @Test
    @DisplayName("a barcode on a carton counts as much as one on the product")
    void codeBarreDUneUniteCompte() {
        when(packagings.existsByBarcodeIsNotNull()).thenReturn(true);

        assertTrue(firstSteps.progress().barcode());
    }

    @Test
    @DisplayName("each step follows what the shop holds")
    void chaqueEtapeSuitLesDonnees() {
        when(products.count()).thenReturn(3L);
        when(products.existsByAverageCostIsNotNull()).thenReturn(true);
        when(products.existsByBarcodeIsNotNull()).thenReturn(true);
        when(products.existsByStockQuantityGreaterThan(BigDecimal.ZERO)).thenReturn(true);
        when(users.countEnabledByRole(RoleApp.CASHIER)).thenReturn(1L);
        when(sales.count()).thenReturn(12L);

        assertEquals(new FirstStepsResponse(true, true, true, true, true, true), firstSteps.progress());
    }
}
