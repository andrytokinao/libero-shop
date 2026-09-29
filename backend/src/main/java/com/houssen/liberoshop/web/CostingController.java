package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CostingService;
import com.houssen.liberoshop.web.dto.MarginReportResponse;
import com.houssen.liberoshop.web.dto.ProductCostResponse;
import com.houssen.liberoshop.web.dto.SupplierPriceResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Purchase costs and margins. Never readable at the counter: what the shop pays its suppliers
 * is for whoever buys the stock and whoever runs the business.
 */
@RestController
@RequestMapping("/api/costing")
public class CostingController {

    private final CostingService costingService;

    public CostingController(CostingService costingService) {
        this.costingService = costingService;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<ProductCostResponse> productCosts() {
        return costingService.findProductCosts();
    }

    @GetMapping("/products/{productId}/suppliers")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<SupplierPriceResponse> supplierPrices(@PathVariable Long productId) {
        return costingService.supplierPricesOf(productId);
    }

    /** @param from first day, inclusive, ISO format; defaults to the first of the month */
    @GetMapping("/margin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public MarginReportResponse margin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return costingService.marginReport(from, to);
    }
}
