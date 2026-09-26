package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.SaleService;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final CurrentUser currentUser;

    public SaleController(SaleService saleService, CurrentUser currentUser) {
        this.saleService = saleService;
        this.currentUser = currentUser;
    }

    /**
     * Records a checkout. The seller is the logged-in cashier, taken from the session --
     * there is no seller field in the payload to forge.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceResponse create(@Valid @RequestBody CreateSaleRequest request) {
        return saleService.checkout(request, currentUser.require());
    }
}
