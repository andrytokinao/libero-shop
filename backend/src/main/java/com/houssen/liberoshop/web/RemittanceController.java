package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.RemittanceService;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.PaymentResponse;
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

/**
 * The cash hand-over: what the depot -- or an order taker allowed to take money -- brings to the till.
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
    @PreAuthorize("hasAnyRole('DEPOT_AGENT', 'ORDER_TAKER')")
    public List<PaymentResponse> cashInHand() {
        return remittanceService.cashInHand(currentUser.require());
    }

    /**
     * Brings cash to the desk: the orders listed, or everything the agent holds when the body is
     * absent or lists none.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_AGENT', 'ORDER_TAKER')")
    public CashRemittanceResponse submit(@RequestBody(required = false) SubmitRemittanceRequest request) {
        return remittanceService.submit(currentUser.require(), request == null ? null : request.invoiceIds());
    }

    /** @param invoiceIds the orders whose cash is brought; empty or null for all of it */
    public record SubmitRemittanceRequest(List<Long> invoiceIds) {
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('CASHIER')")
    public CashRemittanceResponse confirm(@PathVariable Long id) {
        return remittanceService.confirm(id, currentUser.require());
    }
}
