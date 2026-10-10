package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Corrects a product after it was created: its name, the price of its base unit, its barcode.
 *
 * <p>Safe for the history, like removing a unit: a sale line copies the price, unit and cost it
 * was sold at, so a new price applies from the next sale and never rewrites a past ticket.
 *
 * <p>The stock never moves here, yet the row is read for update all the same: Hibernate writes
 * every column of a changed row, and a sale committing in between would have its new quantity
 * overwritten by the one read before it.
 */
@Service
public class ProductEditService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;
    private final PackagingRules rules;
    private final ApplicationEventPublisher events;

    public ProductEditService(ProductRepository products, ProductPackagingRepository packagings,
                              PackagingRules rules, ApplicationEventPublisher events) {
        this.products = products;
        this.packagings = packagings;
        this.rules = rules;
        this.events = events;
    }

    /**
     * @throws BusinessRuleException naming what to fix, in French
     */
    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse edit(Long productId, UpdateProductRequest request) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId));
        String name = PackagingRules.squeeze(request.name());
        if (name == null) {
            throw new BusinessRuleException("PRODUCT_NAME_REQUIRED", "Le nom du produit est obligatoire.");
        }
        if (request.price() == null || request.price().signum() <= 0) {
            throw new BusinessRuleException("PRODUCT_PRICE_INVALID",
                    "Le prix du produit doit etre superieur a zero.");
        }
        String barcode = PackagingRules.squeeze(request.barcode());
        if (barcode != null && !barcode.equals(product.getBarcode()) && rules.barcodeTaken(barcode)) {
            throw new BusinessRuleException("BARCODE_TAKEN",
                    "Le code-barres " + barcode + " est deja utilise par un autre article.");
        }

        product.setName(name);
        product.setPrice(request.price());
        product.setBarcode(barcode);
        // Not a stock movement, but the same message serves: it carries the products as they
        // now stand, which is how an open till learns the new price before the next sale.
        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return ProductUnitsResponse.of(product, packagings.findByProductIdOrderByFactorAsc(product.getId()));
    }
}
