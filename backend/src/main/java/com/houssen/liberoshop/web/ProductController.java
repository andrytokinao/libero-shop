package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.ProductEditService;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Corrects one product's name, price and barcode. Reserved, like its units, to the roles that
 * own the catalogue.
 *
 * <p>Answers the same picture as {@link ProductUnitController}: the edit is made from the units
 * dialog, which re-renders from whatever the server holds.
 */
@RestController
@RequestMapping("/api/products/{productId}")
public class ProductController {

    private final ProductEditService productEditService;

    public ProductController(ProductEditService productEditService) {
        this.productEditService = productEditService;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse edit(@PathVariable Long productId,
                                     @Valid @RequestBody UpdateProductRequest request) {
        return productEditService.edit(productId, request);
    }
}
