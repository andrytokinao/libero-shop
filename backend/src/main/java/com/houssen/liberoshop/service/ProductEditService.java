package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.CreateProductRequest;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product's sheet, written by hand: created one at a time -- what the import does by the
 * hundred -- and corrected afterwards: its name, the price of its base unit, its barcode, its
 * rayon, its purchase cost.
 *
 * <p>Safe for the history, like removing a unit: a sale line copies the price, unit and cost it
 * was sold at, so a new price applies from the next sale and never rewrites a past ticket.
 *
 * <p>The stock never moves in an edit, yet the row is read for update all the same: Hibernate
 * writes every column of a changed row, and a sale committing in between would have its new
 * quantity overwritten by the one read before it. A creation is the one place here that sets a
 * stock, and it books the entry that explains it.
 */
@Service
public class ProductEditService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;
    private final CategoryRepository categories;
    private final StockMovementRepository movements;
    private final PackagingRules rules;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    public ProductEditService(ProductRepository products, ProductPackagingRepository packagings,
                              CategoryRepository categories, StockMovementRepository movements,
                              PackagingRules rules, BusinessCalendar calendar,
                              ApplicationEventPublisher events) {
        this.products = products;
        this.packagings = packagings;
        this.categories = categories;
        this.movements = movements;
        this.rules = rules;
        this.calendar = calendar;
        this.events = events;
    }

    /**
     * Creates a product. What is already on the shelf goes in as a receipt without a supplier,
     * exactly as an import books it, so the first units are in the history like every later one.
     *
     * <p>A name already taken is refused rather than freed with a suffix as the import does: the
     * operator is in front of the form and can say which of the two they meant.
     *
     * @param author who is credited with the starting stock
     * @throws BusinessRuleException naming what to fix, in French
     */
    @RequiresActiveLicense
    @Transactional
    public ProductResponse create(CreateProductRequest request, UserApp author) {
        String name = requireName(request.name());
        BigDecimal price = requirePrice(request.price());
        if (products.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("PRODUCT_NAME_TAKEN",
                    "Un produit s'appelle deja \"" + name + "\".");
        }
        String barcode = PackagingRules.squeeze(request.barcode());
        if (barcode != null && rules.barcodeTaken(barcode)) {
            throw barcodeTaken(barcode);
        }
        BigDecimal stock = request.initialStock() == null ? BigDecimal.ZERO : request.initialStock();
        if (Quantities.tooPrecise(stock)) {
            throw new BusinessRuleException("QUANTITY_TOO_PRECISE",
                    "Une quantite se saisit au millieme au plus (" + stock + ").");
        }

        Product fresh = Product.builder()
                .name(name)
                .price(price)
                .stockQuantity(Quantities.of(0))
                .unit(PackagingRules.squeeze(request.unit()))
                .barcode(barcode)
                .category(categoryOf(request.categoryId()))
                .build();
        // Received like any delivery, so the product starts with its cost as average.
        BigDecimal unitCost = PurchaseCosting.scaled(request.purchaseCost());
        PurchaseCosting.receive(fresh, Quantities.scaled(stock), unitCost);
        Product product = products.save(fresh);
        if (stock.signum() > 0) {
            movements.save(Supply.builder()
                    .quantity(Quantities.scaled(stock))
                    .movementDate(calendar.now())
                    .product(product)
                    .performedBy(author)
                    .unitCost(unitCost)
                    .build());
        }

        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return ProductResponse.of(product);
    }

    /**
     * @throws BusinessRuleException naming what to fix, in French
     */
    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse edit(Long productId, UpdateProductRequest request) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId));
        String name = requireName(request.name());
        BigDecimal price = requirePrice(request.price());
        String barcode = PackagingRules.squeeze(request.barcode());
        if (barcode != null && !barcode.equals(product.getBarcode()) && rules.barcodeTaken(barcode)) {
            throw barcodeTaken(barcode);
        }
        Category category = categoryOf(request.categoryId());
        ProductPackaging purchaseUnit = purchaseUnitOf(product, request.purchaseUnitId());

        product.setName(name);
        product.setPrice(price);
        product.setBarcode(barcode);
        product.setCategory(category);
        product.setPurchaseUnit(purchaseUnit);
        if (request.purchaseCost() != null) {
            // The one place the average is set rather than computed: the operator knows what the
            // shelf cost when no receipt said it, or said it wrong. Typed per purchase unit -- a
            // tonne -- and kept per base unit. The next costed receipt blends into this figure
            // like into any other.
            product.setAverageCost(PurchaseCosting.averageFrom(request.purchaseCost(),
                    purchaseUnit == null ? BigDecimal.ONE : purchaseUnit.getFactor()));
        }
        // Not a stock movement, but the same message serves: it carries the products as they
        // now stand, which is how an open till learns the new price before the next sale.
        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return ProductUnitsResponse.of(product, packagings.findByProductIdOrderByFactorAsc(product.getId()));
    }

    // ------------------------------------------------------------------- internals

    private static String requireName(String typed) {
        String name = PackagingRules.squeeze(typed);
        if (name == null) {
            throw new BusinessRuleException("PRODUCT_NAME_REQUIRED", "Le nom du produit est obligatoire.");
        }
        return name;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessRuleException("PRODUCT_PRICE_INVALID",
                    "Le prix du produit doit etre superieur a zero.");
        }
        return price;
    }

    private static BusinessRuleException barcodeTaken(String barcode) {
        return new BusinessRuleException("BARCODE_TAKEN",
                "Le code-barres " + barcode + " est deja utilise par un autre article.");
    }

    /** One of the product's own units, or null for the base unit. */
    private ProductPackaging purchaseUnitOf(Product product, Long packagingId) {
        if (packagingId == null) {
            return null;
        }
        return packagings.findById(packagingId)
                .filter(packaging -> packaging.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new BusinessRuleException("PURCHASE_UNIT_UNKNOWN",
                        "Cette unite d'achat n'existe plus pour ce produit. Rechargez la fiche."));
    }

    /** The rayon of that id; null for none. */
    private Category categoryOf(Long categoryId) {
        return categoryId == null ? null
                : categories.findById(categoryId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Categorie", categoryId));
    }
}
