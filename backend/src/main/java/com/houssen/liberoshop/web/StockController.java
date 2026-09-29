package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.StockOutputResponse;
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

    @GetMapping("/outputs")
    public List<StockOutputResponse> outputs(@RequestParam(required = false) String search) {
        return stockService.findOutputs(search);
    }

    /** Goods received. Only the depot manager books an entry into the stock. */
    @PostMapping("/supplies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEPOT_MANAGER')")
    public SupplyResponse registerSupply(@Valid @RequestBody CreateSupplyRequest request) {
        return stockService.registerSupply(request, currentUser.require());
    }
}
