package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateInventoryRequest;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * A rayon counted in one go. Each product whose count differs from the stock gets an inventory
 * correction, exactly as if it had been counted alone; those found as the stock says are left
 * untouched. All or nothing: one refused line and no correction is written, so the count can be
 * fixed and sent again without half of it already booked.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final StockService stock;
    private final ProductRepository products;
    private final CategoryRepository categories;

    public InventoryService(StockService stock, ProductRepository products, CategoryRepository categories) {
        this.stock = stock;
        this.products = products;
        this.categories = categories;
    }

    /**
     * @return the corrections written, one per product whose count differed
     * @throws BusinessRuleException when a product is counted twice
     */
    @RequiresActiveLicense
    @Transactional
    public List<StockAdjustmentResponse> count(CreateInventoryRequest request, UserApp author) {
        refuseTwice(request.lines());
        String comment = commentFor(request.categoryId());
        // In id order, as the deliveries lock them: two counts never wait on each other.
        return request.lines().stream()
                .sorted(Comparator.comparing(CreateInventoryRequest.Line::productId))
                .filter(this::differsFromStock)
                .map(line -> stock.registerAdjustment(new CreateStockAdjustmentRequest(line.productId(),
                        AdjustmentReason.INVENTORY_COUNT, line.counted(), comment), author))
                .toList();
    }

    private boolean differsFromStock(CreateInventoryRequest.Line line) {
        return products.findById(line.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", line.productId()))
                .getStockQuantity().compareTo(line.counted()) != 0;
    }

    private String commentFor(Long categoryId) {
        if (categoryId == null) {
            return "Inventaire";
        }
        return "Inventaire du rayon " + categories.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Rayon", categoryId))
                .getName();
    }

    private static void refuseTwice(List<CreateInventoryRequest.Line> lines) {
        if (lines.stream().map(CreateInventoryRequest.Line::productId).distinct().count() < lines.size()) {
            throw new BusinessRuleException("INVENTORY_PRODUCT_TWICE",
                    "Un meme produit est compte deux fois : gardez une seule ligne par produit.");
        }
    }
}
