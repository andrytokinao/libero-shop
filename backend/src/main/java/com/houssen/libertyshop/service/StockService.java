package com.houssen.libertyshop.service;

import com.houssen.libertyshop.entity.Product;
import com.houssen.libertyshop.entity.Supplier;
import com.houssen.libertyshop.entity.Supply;
import com.houssen.libertyshop.entity.UserApp;
import com.houssen.libertyshop.license.RequiresActiveLicense;
import com.houssen.libertyshop.repository.ProductRepository;
import com.houssen.libertyshop.repository.StockMovementRepository;
import com.houssen.libertyshop.repository.SupplierRepository;
import com.houssen.libertyshop.service.exception.ResourceNotFoundException;
import com.houssen.libertyshop.web.dto.CreateSupplyRequest;
import com.houssen.libertyshop.web.dto.StockOutputResponse;
import com.houssen.libertyshop.web.dto.SupplyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public StockService(StockMovementRepository movements, ProductRepository products,
                        SupplierRepository suppliers, BusinessCalendar calendar) {
        this.movements = movements;
        this.products = products;
        this.suppliers = suppliers;
        this.calendar = calendar;
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

    /** Receives goods: raises the stock and records the movement that explains the rise. */
    @RequiresActiveLicense
    @Transactional
    public SupplyResponse registerSupply(CreateSupplyRequest request, UserApp receiver) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", request.productId()));
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", request.supplierId()));

        product.adjustStock(request.quantity());

        Supply supply = movements.save(Supply.builder()
                .quantity(request.quantity())
                .movementDate(calendar.now())
                .product(product)
                .performedBy(receiver)
                .supplier(supplier)
                .build());

        return SupplyResponse.of(supply);
    }

    public long countSuppliesLastWeek() {
        return movements.countSuppliesSince(calendar.startOfDaysAgo(7));
    }

    public long unitsSuppliedLastWeek() {
        return movements.sumSuppliedUnitsSince(calendar.startOfDaysAgo(7));
    }
}
