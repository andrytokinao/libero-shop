package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.ProductUnitService;
import com.houssen.liberoshop.web.dto.BaseUnitRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The units a product is sold in.
 *
 * <p>Read by everyone -- a till offers the units it sells in. Written only by the two roles that
 * own the catalogue: a wrong factor moves the stock wrongly on every later sale without anything
 * looking amiss, so a cashier choosing a unit must never be able to define one.
 *
 * <p>Every write answers with the product's whole set of units -- see {@link ProductUnitsResponse}.
 */
@RestController
@RequestMapping("/api/products/{productId}/units")
public class ProductUnitController {

    private final ProductUnitService productUnitService;

    public ProductUnitController(ProductUnitService productUnitService) {
        this.productUnitService = productUnitService;
    }

    @GetMapping
    public ProductUnitsResponse units(@PathVariable Long productId) {
        return productUnitService.unitsOf(productId);
    }

    @PutMapping("/base")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse renameBase(@PathVariable Long productId,
                                           @Valid @RequestBody BaseUnitRequest request) {
        return productUnitService.renameBaseUnit(productId, request);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse add(@PathVariable Long productId,
                                    @Valid @RequestBody PackagingRequest request) {
        return productUnitService.add(productId, request);
    }

    @PutMapping("/{packagingId}")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse update(@PathVariable Long productId,
                                       @PathVariable Long packagingId,
                                       @Valid @RequestBody PackagingRequest request) {
        return productUnitService.update(productId, packagingId, request);
    }

    @DeleteMapping("/{packagingId}")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse remove(@PathVariable Long productId, @PathVariable Long packagingId) {
        return productUnitService.remove(productId, packagingId);
    }
}
