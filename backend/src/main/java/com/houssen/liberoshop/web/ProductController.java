package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.ProductEditService;
import com.houssen.liberoshop.web.dto.AlertThresholdRequest;
import com.houssen.liberoshop.web.dto.CreateProductRequest;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creates one product by hand and corrects its sheet: name, price, barcode, rayon, purchase
 * cost. Reserved, like its units and like the import, to the roles that own the catalogue.
 *
 * <p>An edit answers the same picture as {@link ProductUnitController}: it is made from the
 * product dialog, which re-renders from whatever the server holds.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductEditService productEditService;
    private final CurrentUser currentUser;

    public ProductController(ProductEditService productEditService, CurrentUser currentUser) {
        this.productEditService = productEditService;
        this.currentUser = currentUser;
    }

    /** The starting stock, if any, is booked under the name of whoever creates the product. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productEditService.create(request, currentUser.require());
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse edit(@PathVariable Long productId,
                                     @Valid @RequestBody UpdateProductRequest request) {
        return productEditService.edit(productId, request);
    }

    /** "Seuil d'alerte" in the product's menu: its own, or back to the shop's common one. */
    @PutMapping("/{productId}/alert-threshold")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse changeAlertThreshold(@PathVariable Long productId,
                                                     @Valid @RequestBody AlertThresholdRequest request) {
        return productEditService.changeAlertThreshold(productId, request.threshold());
    }
}
