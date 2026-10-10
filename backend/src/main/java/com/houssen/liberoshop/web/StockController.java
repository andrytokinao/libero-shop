package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import com.houssen.liberoshop.web.dto.StockOutputPageResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final CurrentUser currentUser;

    public StockController(StockService stockService, CurrentUser currentUser) {
        this.stockService = stockService;
        this.currentUser = currentUser;
    }

    /**
     * The receipts, with what each cost. Restricted since receipts carry purchase prices: the
     * margin a shop makes is not the counter's business, and only the stock screens read this.
     */
    @GetMapping("/supplies")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<SupplyResponse> supplies() {
        return stockService.findSupplies();
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

    /** Goods received. Only the depot manager books an entry into the stock. */
    @PostMapping("/supplies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public SupplyResponse registerSupply(@Valid @RequestBody CreateSupplyRequest request) {
        return stockService.registerSupply(request, currentUser.require());
    }

    /** The corrections made by hand, with what each loss cost -- hence the same roles as receipts. */
    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<StockAdjustmentResponse> adjustments() {
        return stockService.findAdjustments();
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
