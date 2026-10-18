package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.CatalogJournal;
import com.houssen.liberoshop.service.ProductEditService;
import com.houssen.liberoshop.web.dto.AlertThresholdRequest;
import com.houssen.liberoshop.web.dto.CatalogChangeResponse;
import com.houssen.liberoshop.web.dto.CreateProductRequest;
import com.houssen.liberoshop.web.dto.RetiredRequest;
import com.houssen.liberoshop.web.dto.SalePriceRequest;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final CatalogJournal journal;
    private final CurrentUser currentUser;

    public ProductController(ProductEditService productEditService, CatalogJournal journal,
                             CurrentUser currentUser) {
        this.productEditService = productEditService;
        this.journal = journal;
        this.currentUser = currentUser;
    }

    /** Who changed this product's sheet, what, and when: its latest changes, newest first. */
    @GetMapping("/{productId}/changes")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<CatalogChangeResponse> changes(@PathVariable Long productId) {
        return journal.latestOf(productId);
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

    /** "Retirer de la vente" / "Remettre en vente" in the product's menu. */
    @PutMapping("/{productId}/retired")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse changeRetired(@PathVariable Long productId,
                                              @Valid @RequestBody RetiredRequest request) {
        return productEditService.changeRetired(productId, request.retired());
    }

    /** A price typed straight into the "Prix et unites" table. */
    @PutMapping("/{productId}/sale-price")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse changeSalePrice(@PathVariable Long productId,
                                                @Valid @RequestBody SalePriceRequest request) {
        return productEditService.changeSalePrice(productId, request.packagingId(), request.price());
    }

    /** "Seuil d'alerte" in the product's menu: its own, or back to the shop's common one. */
    @PutMapping("/{productId}/alert-threshold")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductUnitsResponse changeAlertThreshold(@PathVariable Long productId,
                                                     @Valid @RequestBody AlertThresholdRequest request) {
        return productEditService.changeAlertThreshold(productId, request.threshold());
    }
}
