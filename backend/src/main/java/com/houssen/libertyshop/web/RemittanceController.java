package com.houssen.libertyshop.web;

import com.houssen.libertyshop.entity.RemittanceStatus;
import com.houssen.libertyshop.security.CurrentUser;
import com.houssen.libertyshop.service.RemittanceService;
import com.houssen.libertyshop.web.dto.CashRemittanceResponse;
import com.houssen.libertyshop.web.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The depot cash hand-over.
 *
 * <p>Neither endpoint takes an actor: the agent remits their own cash and the desk confirms
 * as itself, both read from the session. That is what keeps the trail meaningful.
 */
@RestController
@RequestMapping("/api/remittances")
public class RemittanceController {

    private final RemittanceService remittanceService;
    private final CurrentUser currentUser;

    public RemittanceController(RemittanceService remittanceService, CurrentUser currentUser) {
        this.remittanceService = remittanceService;
        this.currentUser = currentUser;
    }

    /** @param mine restrict to the slips the caller submitted */
    @GetMapping
    public List<CashRemittanceResponse> search(@RequestParam(required = false) RemittanceStatus status,
                                               @RequestParam(defaultValue = "false") boolean mine) {
        Long submittedById = mine ? currentUser.require().getId() : null;
        return remittanceService.search(status, submittedById);
    }

    /** What the calling agent has collected and not handed over yet. */
    @GetMapping("/cash-in-hand")
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public List<PaymentResponse> cashInHand() {
        return remittanceService.cashInHand(currentUser.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public CashRemittanceResponse submit() {
        return remittanceService.submit(currentUser.require());
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('CASHIER')")
    public CashRemittanceResponse confirm(@PathVariable Long id) {
        return remittanceService.confirm(id, currentUser.require());
    }
}
