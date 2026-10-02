package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.StockOutputResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Stock movements: goods in (SUPPLY) and goods out (OUTPUT).
 *
 * <p>Outputs are never written here -- they are produced by {@link SaleService} as part of
 * the checkout transaction. This service only reads them, which keeps a single place able
 * to take stock out and makes the ledger match the invoices by construction.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    public StockService(StockMovementRepository movements, ProductRepository products,
                        SupplierRepository suppliers, BusinessCalendar calendar,
                        ApplicationEventPublisher events) {
        this.movements = movements;
        this.products = products;
        this.suppliers = suppliers;
        this.calendar = calendar;
        this.events = events;
    }

    public List<SupplyResponse> findSupplies() {
        return movements.findSupplies().stream().map(SupplyResponse::of).toList();
    }

    /** @param search free text on product name, invoice number and client */
    public List<StockOutputResponse> findOutputs(String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return movements.findOutputs().stream()
                .map(StockOutputResponse::of)
                .filter(output -> needle.isEmpty()
                        || output.product().name().toLowerCase(Locale.ROOT).contains(needle)
                        || output.invoice().invoiceNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || output.invoice().clientName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * Receives goods: raises the stock, blends their cost into the product's average, and
     * records the movement that explains both.
     */
    @RequiresActiveLicense
    @Transactional
    public SupplyResponse registerSupply(CreateSupplyRequest request, UserApp receiver) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", request.productId()));
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", request.supplierId()));
        BigDecimal unitCost = PurchaseCosting.scaled(request.unitCost());

        PurchaseCosting.receive(product, request.quantity(), unitCost);

        Supply supply = movements.save(Supply.builder()
                .quantity(request.quantity())
                .movementDate(calendar.now())
                .product(product)
                .performedBy(receiver)
                .supplier(supplier)
                .unitCost(unitCost)
                .build());

        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return SupplyResponse.of(supply);
    }

    public long countSuppliesLastWeek() {
        return movements.countSuppliesSince(calendar.startOfDaysAgo(7));
    }

    public long unitsSuppliedLastWeek() {
        return movements.sumSuppliedUnitsSince(calendar.startOfDaysAgo(7));
    }
}
