package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.SaleService;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
     * Records a checkout, or an order taken from a phone. The seller is the logged-in account,
     * taken from the session -- there is no seller field in the payload to forge.
     *
     * <p>Posted twice with the same reference, a basket is one sale: the second post is
     * answered with the order the first one made -- see {@code SaleService#checkout}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CASHIER', 'ORDER_TAKER')")
    public InvoiceResponse create(@Valid @RequestBody CreateSaleRequest request) {
        UserApp seller = currentUser.require();
        try {
            return saleService.checkout(request, seller);
        } catch (DataIntegrityViolationException refused) {
            // The database's own word on a reference written twice: two posts of one basket
            // that did not share a product lock to queue on. The first one's order is the answer.
            return saleService.recorded(SaleService.referenceOf(request), seller).orElseThrow(() -> refused);
        }
    }
}
