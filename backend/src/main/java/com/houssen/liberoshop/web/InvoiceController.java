package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.web.dto.CancelInvoiceRequest;
import com.houssen.liberoshop.web.dto.DeliveryResponse;
import jakarta.validation.Valid;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.PayInvoiceRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Hand-over at the depot; settles an unpaid order in cash on the spot, unless {@code collect}
     * is false -- the customer pays at the till.
     */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public DeliveryResponse deliver(@PathVariable Long id,
                                    @RequestParam(defaultValue = "true") boolean collect) {
        InvoiceService.DeliveryResult result = invoiceService.deliver(id, currentUser.require(), collect);
        return new DeliveryResponse(result.invoice(), result.collected(), messageOf(result));
    }

    /** "Je m'en occupe": the order is the caller's to prepare; the other storekeepers leave it. */
    @PostMapping("/{id}/take")
    @PreAuthorize("hasRole('DEPOT_AGENT')")
    public InvoiceResponse take(@PathVariable Long id) {
        return invoiceService.takeOver(id, currentUser.require());
    }

    /** Back to the queue: by whoever took it, or by whoever runs the depot or the shop. */
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('DEPOT_AGENT', 'DEPOT_MANAGER', 'SUPER_ADMIN')")
    public InvoiceResponse release(@PathVariable Long id) {
        return invoiceService.release(id, currentUser.require());
    }

    /** An order taker takes the cash of an unpaid order, to bring to the till later. */
    @PostMapping("/{id}/collect")
    @PreAuthorize("hasRole('ORDER_TAKER')")
    public InvoiceResponse collect(@PathVariable Long id) {
        return invoiceService.collect(id, currentUser.require());
    }

    /** Settles an unpaid order at the till. */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceResponse pay(@PathVariable Long id, @RequestBody(required = false) PayInvoiceRequest request) {
        return invoiceService.pay(id, request == null ? null : request.paymentMethod(), currentUser.require());
    }

    /** Cancels an unpaid order; the service decides whose and whether its goods return to stock. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CASHIER', 'ORDER_TAKER', 'SUPER_ADMIN')")
    public InvoiceResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelInvoiceRequest request) {
        return invoiceService.cancel(id, request.reason(), request.comment(), currentUser.require());
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
        if (result.invoice().paymentStatus() == PaymentStatus.UNPAID) {
            return "Commande " + number + " remise a " + result.invoice().clientName()
                    + ". Le paiement se regle a la caisse.";
        }
        return "Commande " + number + " remise a " + result.invoice().clientName() + ".";
    }
}
