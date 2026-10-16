package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ApplyUnitModelRequest;
import com.houssen.liberoshop.web.dto.BaseUnitRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The units a product is sold in: its base unit, and the packagings counted in it.
 *
 * <p>The stock never moves here. Renaming the base unit or adding "kg = 3.5 kapoka" changes how
 * the same quantity is described and sold, not how much there is -- which is why none of this
 * goes through {@code StockService}.
 *
 * <p>What a unit may be is decided by {@link PackagingRules}, shared with the product import;
 * this service only finds the rows and writes them.
 *
 * <p>Writes are licensed, reads are not, like the rest of the catalogue.
 */
@Service
@Transactional(readOnly = true)
public class ProductUnitService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;
    private final PackagingRules rules;
    private final UnitModelApplication modelApplication;

    public ProductUnitService(ProductRepository products, ProductPackagingRepository packagings,
                              PackagingRules rules, UnitModelApplication modelApplication) {
        this.products = products;
        this.packagings = packagings;
        this.rules = rules;
        this.modelApplication = modelApplication;
    }

    public ProductUnitsResponse unitsOf(Long productId) {
        return responseFor(requireProduct(productId));
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse renameBaseUnit(Long productId, BaseUnitRequest request) {
        // For update, though the stock is not touched: Hibernate writes every column of the
        // row, and a sale committing in between would get its quantity overwritten.
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId));
        String unit = PackagingRules.squeeze(request.unit());
        if (unit != null) {
            rules.refuseBaseUnitTaken(product, unit);
        }
        product.setUnit(unit);
        return responseFor(product);
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse add(Long productId, PackagingRequest request) {
        Product product = requireProduct(productId);
        ProductPackaging packaging = ProductPackaging.builder().product(product).build();
        rules.applyTo(request, packaging);
        packagings.save(packaging);
        return responseFor(product);
    }

    /**
     * Units ticked in a model of units ("Poids": g, kg, tonne), each at the price typed for this
     * product. All or nothing: one refused unit and none is added.
     */
    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse addFromModel(Long productId, ApplyUnitModelRequest request) {
        Product product = requireProduct(productId);
        modelApplication.applyTo(product, request);
        return responseFor(product);
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse update(Long productId, Long packagingId, PackagingRequest request) {
        ProductPackaging packaging = requirePackaging(productId, packagingId);
        rules.applyTo(request, packaging);
        return responseFor(packaging.getProduct());
    }

    /**
     * Safe for the history: a sale line copies the label, factor and price it was sold at, so
     * removing a unit never rewrites a past ticket. Refused only while another unit is written
     * in this one -- see {@link PackagingRules#refuseRemoval}.
     */
    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse remove(Long productId, Long packagingId) {
        // Locked first, so the product read below is the current row: when it was bought in this
        // unit it is written back without it, and must not carry a stale stock with it.
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId));
        ProductPackaging packaging = requirePackaging(productId, packagingId);
        rules.refuseRemoval(packaging);
        if (packaging.underlies(product.getPurchaseUnit())) {
            // Its price is kept per base unit: only the unit it is shown in falls back.
            product.setPurchaseUnit(null);
            products.flush();
        }
        packagings.delete(packaging);
        packagings.flush();
        return responseFor(product);
    }

    // ------------------------------------------------------------------- internals

    private ProductUnitsResponse responseFor(Product product) {
        return ProductUnitsResponse.of(product,
                packagings.findByProductIdOrderByFactorAsc(product.getId()));
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Produit", id));
    }

    /** Addressed through its product, so a stale screen cannot edit another product's unit. */
    private ProductPackaging requirePackaging(Long productId, Long packagingId) {
        return packagings.findById(packagingId)
                .filter(packaging -> packaging.getProduct().getId().equals(productId))
                .orElseThrow(() -> ResourceNotFoundException.of("Unite", packagingId));
    }
}
