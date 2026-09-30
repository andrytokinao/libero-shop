package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.web.dto.DeliveryResponse;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    public InvoiceController(InvoiceService invoiceService, CurrentUser currentUser) {
        this.invoiceService = invoiceService;
        this.currentUser = currentUser;
    }

    /**
     * @param mine when true, restrict to the invoices issued by the caller -- that is how
     *             a cash desk gets "my invoices" without being able to ask for someone else's
     */
    @GetMapping
    public List<InvoiceResponse> search(@RequestParam(defaultValue = "false") boolean mine,
                                        @RequestParam(required = false) PaymentStatus paymentStatus,
                                        @RequestParam(required = false) DeliveryStatus deliveryStatus,
                                        @RequestParam(defaultValue = "false") boolean todayOnly,
                                        @RequestParam(required = false) String search) {
        Long sellerId = mine ? currentUser.require().getId() : null;
        return invoiceService.search(sellerId, paymentStatus, deliveryStatus, todayOnly, search);
    }

    @GetMapping("/{id}")
    public InvoiceResponse byId(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    /** Hand-over at the depot; settles an unpaid order in cash on the spot. */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public DeliveryResponse deliver(@PathVariable Long id) {
        InvoiceService.DeliveryResult result = invoiceService.deliver(id, currentUser.require());
        return new DeliveryResponse(result.invoice(), result.collected(), messageOf(result));
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('CASHIER', 'SUPER_ADMIN')")
    public InvoiceResponse print(@PathVariable Long id) {
        return invoiceService.markPrinted(id);
    }

    private static String messageOf(InvoiceService.DeliveryResult result) {
        String number = result.invoice().invoiceNumber();
        if (result.collected().compareTo(BigDecimal.ZERO) > 0) {
            return number + " remise. " + result.collected().toPlainString()
                    + " Ar encaisses en especes : touchez \"Remettre a la caisse\" en apportant l'argent.";
        }
        return "Commande " + number + " remise a " + result.invoice().clientName() + ".";
    }
}
