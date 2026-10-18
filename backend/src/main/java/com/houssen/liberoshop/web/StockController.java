package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.DeliveryService;
import com.houssen.liberoshop.service.InventoryService;
import com.houssen.liberoshop.service.MovementCancellationService;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.web.dto.AdjustmentSummaryResponse;
import com.houssen.liberoshop.web.dto.CancelDeliveryRequest;
import com.houssen.liberoshop.web.dto.CreateDeliveryRequest;
import com.houssen.liberoshop.web.dto.CreateInventoryRequest;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.ProductMovementResponse;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import com.houssen.liberoshop.web.dto.StockOutputPageResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;
    private final DeliveryService deliveryService;
    private final MovementCancellationService cancellations;
    private final InventoryService inventories;
    private final CurrentUser currentUser;

    public StockController(StockService stockService, DeliveryService deliveryService,
                           MovementCancellationService cancellations, InventoryService inventories,
                           CurrentUser currentUser) {
        this.stockService = stockService;
        this.deliveryService = deliveryService;
        this.cancellations = cancellations;
        this.inventories = inventories;
        this.currentUser = currentUser;
    }

    /**
     * The receipts, with what each cost. Restricted since receipts carry purchase prices: the
     * margin a shop makes is not the counter's business, and only the stock screens read this.
     */
    @GetMapping("/supplies")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<SupplyResponse> supplies(@RequestParam(required = false) Long supplierId) {
        return supplierId == null ? stockService.findSupplies() : stockService.findSuppliesOf(supplierId);
    }

    /**
     * What left the stock, a page at a time, with its value: the same readers as what came in.
     * The totals cover every output matching the search, not only the page returned.
     */
    @GetMapping("/outputs")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public StockOutputPageResponse outputs(@RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "" + StockService.DEFAULT_PAGE_SIZE) int size) {
        return stockService.findOutputs(search, page, size);
    }

    /**
     * Everything that moved one product's stock -- receipts, sales, corrections -- a page at a
     * time. Receipts carry purchase prices, hence the same readers as the lists above.
     */
    @GetMapping("/products/{productId}/movements")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public ProductMovementResponse.Page movementsOf(@PathVariable Long productId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return stockService.findMovementsOf(productId, page, size);
    }

    /** Goods received. Only the depot manager books an entry into the stock. */
    @PostMapping("/supplies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public SupplyResponse registerSupply(@Valid @RequestBody CreateSupplyRequest request) {
        return stockService.registerSupply(request, currentUser.require());
    }

    /** A delivery note: several products from one supplier, booked together or not at all. */
    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public List<SupplyResponse> registerDelivery(@Valid @RequestBody CreateDeliveryRequest request) {
        return deliveryService.receive(request, currentUser.require());
    }

    /** A receipt entered by mistake, put back by an opposite correction; the receipt stays, marked. */
    @PostMapping("/supplies/{supplyId}/cancel")
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public StockAdjustmentResponse cancelSupply(@PathVariable Long supplyId) {
        return cancellations.cancelSupply(supplyId, currentUser.require());
    }

    /** A whole delivery note taken back: every receipt or none. */
    @PostMapping("/deliveries/cancel")
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public List<StockAdjustmentResponse> cancelDelivery(@Valid @RequestBody CancelDeliveryRequest request) {
        return cancellations.cancelDelivery(request.supplyIds(), currentUser.require());
    }

    /** A rayon counted in one go: a correction per product found different, all or none. */
    @PostMapping("/inventories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<StockAdjustmentResponse> registerInventory(@Valid @RequestBody CreateInventoryRequest request) {
        return inventories.count(request, currentUser.require());
    }

    /** A correction entered by mistake, put back the same way. */
    @PostMapping("/adjustments/{adjustmentId}/cancel")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public StockAdjustmentResponse cancelAdjustment(@PathVariable Long adjustmentId) {
        return cancellations.cancelAdjustment(adjustmentId, currentUser.require());
    }

    /** The corrections made by hand, with what each loss cost -- hence the same roles as receipts. */
    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<StockAdjustmentResponse> adjustments() {
        return stockService.findAdjustments();
    }

    /** What every correction adds up to, while the list above holds only the latest. */
    @GetMapping("/adjustments/summary")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public AdjustmentSummaryResponse adjustmentSummary() {
        return stockService.summarizeAdjustments();
    }

    /**
     * Breakage, loss, or a shelf counted. Reserved to those who answer for the stock: a
     * correction is the one movement nothing else vouches for.
     */
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public StockAdjustmentResponse registerAdjustment(@Valid @RequestBody CreateStockAdjustmentRequest request) {
        return stockService.registerAdjustment(request, currentUser.require());
    }
}
