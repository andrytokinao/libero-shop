package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.CancelReason;
import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.DeliveryTransition;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.PaymentTransition;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.InvoiceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Invoice lookup and the depot hand-over.
 *
 * <p>Handing an unpaid order over is the moment the money changes hands, so it is also the
 * moment a {@link Payment} is written, collected by the agent doing the hand-over, and the order
 * becomes {@link PaymentStatus#COLLECTED}. That payment stays un-remitted until the agent takes
 * the cash to the desk ({@link RemittanceService}), which is what makes the whole depot-cash
 * trail auditable.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;
    private final ShopSettingsService settings;
    private final ProductRepository products;
    private final StockMovementRepository movements;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, BusinessCalendar calendar,
                          ApplicationEventPublisher events, ShopSettingsService settings,
                          ProductRepository products, StockMovementRepository movements) {
        this.invoices = invoices;
        this.payments = payments;
        this.calendar = calendar;
        this.events = events;
        this.settings = settings;
        this.products = products;
        this.movements = movements;
    }

    /**
     * The one query behind every invoice list in the UI.
     *
     * @param sellerId       restrict to one seller, or null for all
     * @param paymentStatus  null for both
     * @param deliveryStatus null for both
     * @param todayOnly      restrict to the current business day
     * @param search         free text on number, client and seller name
     */
    public List<InvoiceResponse> search(Long sellerId, PaymentStatus paymentStatus,
                                        DeliveryStatus deliveryStatus, boolean todayOnly, String search) {
        LocalDateTime from = todayOnly ? calendar.startOfToday() : null;
        LocalDateTime to = todayOnly ? calendar.startOfTomorrow() : null;
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        return withCashTrail(invoices.search(sellerId, paymentStatus, deliveryStatus, from, to).stream()
                .filter(invoice -> needle.isEmpty() || matches(invoice, needle))
                .toList());
    }

    public InvoiceResponse findById(Long id) {
        return withCashTrail(List.of(load(id))).getFirst();
    }

    /**
     * The invoices as responses, each carrying where its cash is if it is not in the till yet.
     * One payment query for the whole list, made only when some row has cash on its way.
     */
    private List<InvoiceResponse> withCashTrail(List<Invoice> found) {
        List<Long> cashOnItsWay = found.stream()
                .filter(invoice -> invoice.getPaymentStatus() == PaymentStatus.COLLECTED
                        || invoice.getPaymentStatus() == PaymentStatus.REMITTED)
                .map(Invoice::getId)
                .toList();
        Map<Long, InvoiceResponse.CashTrail> trails = cashOnItsWay.isEmpty()
                ? Map.of()
                : payments.findCashOutsideTill(cashOnItsWay).stream()
                        .collect(Collectors.toMap(payment -> payment.getInvoice().getId(),
                                InvoiceResponse.CashTrail::of, (first, second) -> first));
        return found.stream()
                .map(invoice -> InvoiceResponse.of(invoice, trails.get(invoice.getId())))
                .toList();
    }

    /**
     * Hands the order to the customer. Settles it in cash first when it was left unpaid at
     * the desk -- unless the shop has the depot take no money, in which case it stays unpaid
     * for the till to {@link #pay settle}.
     *
     * @return the updated invoice; {@code collected} tells the UI how much was taken in
     */
    @RequiresActiveLicense
    @Transactional
    public DeliveryResult deliver(Long invoiceId, UserApp agent) {
        return deliver(invoiceId, agent, true);
    }

    /**
     * @param collect false when the customer will pay at the till instead: the waiter serves and
     *                leaves the bill to the cash desk. Ignored when the shop takes no money on
     *                hand-over, where nothing is ever collected.
     */
    @RequiresActiveLicense
    @Transactional
    public DeliveryResult deliver(Long invoiceId, UserApp agent, boolean collect) {
        Invoice invoice = load(invoiceId);
        // Refused before any payment is written for it.
        invoice.check(DeliveryTransition.HAND_OVER);

        BigDecimal collected = BigDecimal.ZERO;
        // When the depot takes no money, an unpaid order leaves unpaid: the bill is settled at the till.
        if (collect && PaymentTransition.COLLECT.appliesTo(invoice.getPaymentStatus())
                && settings.features().payAtDepot()) {
            collected = collectInHand(invoice, agent);
        }

        invoice.apply(DeliveryTransition.HAND_OVER);
        // Heard after the commit: the other depot screens drop the order from their queue, and
        // the seller's screens show it handed over.
        events.publishEvent(new OrderDeliveredEvent(invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getClientName(), invoice.getSale().getSeller().getId(),
                agent.getId(), agent.getFullName(), collected));
        return new DeliveryResult(InvoiceResponse.of(invoice), collected);
    }

    /**
     * An order taker takes the customer's cash for an unpaid order -- the guest paying at the
     * reception. Their cash in hand, brought to the till like the depot's; only when the shop lets
     * order takers take money.
     */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse collect(Long invoiceId, UserApp orderTaker) {
        if (!settings.features().orderTakerCollects()) {
            throw new BusinessRuleException("ORDER_TAKER_CANNOT_COLLECT",
                    "La prise de commande n'encaisse pas dans cette boutique : le client paie a la caisse.");
        }
        Invoice invoice = load(invoiceId);
        collectInHand(invoice, orderTaker);
        events.publishEvent(new OrderPaidEvent(invoice.getId(), invoice.getInvoiceNumber()));
        return InvoiceResponse.of(invoice);
    }

    /**
     * Writes the cash payment held by {@code collector}: the order becomes
     * {@link PaymentStatus#COLLECTED}, not PAID, until a cashier confirms receiving the money.
     *
     * @return the amount taken
     */
    private BigDecimal collectInHand(Invoice invoice, UserApp collector) {
        invoice.check(PaymentTransition.COLLECT);
        BigDecimal amount = invoice.getSale().getTotalAmount();
        payments.save(Payment.builder()
                .amount(amount)
                .paymentMethod(PaymentMethod.CASH)
                .paymentDate(calendar.now())
                .invoice(invoice)
                .collectedBy(collector)
                // Left null: the collector holds the cash until they remit it.
                .cashRemittance(null)
                .build());
        invoice.apply(PaymentTransition.COLLECT);
        return amount;
    }

    /**
     * Settles an unpaid order at the till: the restaurant's bill, the customer who comes back to
     * pay, or any order when the depot does not take money. Paid at once -- the cashier is the one
     * holding the money, so there is nothing left to hand over or confirm.
     */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse pay(Long invoiceId, PaymentMethod method, UserApp cashier) {
        Invoice invoice = load(invoiceId);
        invoice.check(PaymentTransition.PAY_AT_TILL);
        payments.save(Payment.builder()
                .amount(invoice.getSale().getTotalAmount())
                .paymentMethod(method == null ? PaymentMethod.CASH : method)
                .paymentDate(calendar.now())
                .invoice(invoice)
                .collectedBy(cashier)
                .cashRemittance(null)
                .build());
        invoice.apply(PaymentTransition.PAY_AT_TILL);

        events.publishEvent(new OrderPaidEvent(invoice.getId(), invoice.getInvoiceNumber()));
        return InvoiceResponse.of(invoice);
    }

    /**
     * Cancels an unpaid order.
     *
     * <p>Not handed over yet, its goods go back on the shelf: the stock outputs it wrote are
     * removed with the units they took, so the stock and its history read as if the order had
     * never left -- the order itself keeps who cancelled it, when and why. Already handed over,
     * the goods are gone: the cancellation is only allowed when the shop says so, and only
     * records what happened (the customer left without paying), with a comment that says it.
     *
     * <p>A paid order is never cancelled here: its money would have to go back too. Whoever only
     * takes orders may cancel their own; a till or the administrator, any.
     */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse cancel(Long invoiceId, CancelReason reason, String comment, UserApp actor) {
        Invoice invoice = load(invoiceId);
        String number = invoice.getInvoiceNumber();
        String note = comment == null || comment.isBlank() ? null : comment.trim();

        // The domain's rule first (only an unpaid order), then the shop's policies.
        invoice.check(PaymentTransition.CANCEL);
        boolean mayCancelAny = actor.hasRole(RoleApp.CASHIER) || actor.hasRole(RoleApp.SUPER_ADMIN);
        if (!mayCancelAny && !invoice.getSale().getSeller().getId().equals(actor.getId())) {
            throw new BusinessRuleException("NOT_YOUR_ORDER",
                    "Seule la caisse peut annuler une commande prise par quelqu'un d'autre.");
        }
        if (reason == CancelReason.OTHER && note == null) {
            throw new BusinessRuleException("COMMENT_REQUIRED", "Precisez le motif de l'annulation.");
        }

        if (invoice.isHandedOver()) {
            if (!settings.features().cancelAfterDelivery()) {
                // The goods' rule refuses it, in its own words.
                invoice.check(DeliveryTransition.CANCEL);
            }
            if (note == null) {
                throw new BusinessRuleException("COMMENT_REQUIRED",
                        "Commande deja remise : notez ce qui s'est passe avec le client.");
            }
        } else {
            giveBackStock(invoice);
        }

        invoice.cancel(actor, reason, note, calendar.now());

        events.publishEvent(new OrderCancelledEvent(invoice.getId(), number));
        return InvoiceResponse.of(invoice);
    }

    private void giveBackStock(Invoice invoice) {
        List<StockOutput> outputs = movements.findOutputsOfInvoice(invoice.getId());
        for (StockOutput output : outputs) {
            // Locked like at checkout: a sale of the same product at that moment must see the units.
            Product product = products.findByIdForUpdate(output.getProduct().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Produit", output.getProduct().getId()));
            product.adjustStock(output.getQuantity());
        }
        movements.deleteAll(outputs);
    }

    /** Front-end counterpart of {@code Invoice#print()}. */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse markPrinted(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        invoice.print();
        return InvoiceResponse.of(invoice);
    }

    public long countPendingDeliveries() {
        return invoices.countByDeliveryStatus(DeliveryStatus.PENDING);
    }

    public long countDelivered() {
        return invoices.countByDeliveryStatus(DeliveryStatus.DELIVERED);
    }

    public long countUnpaid() {
        return invoices.countByPaymentStatus(PaymentStatus.UNPAID);
    }

    public BigDecimal unpaidAmount() {
        return invoices.sumAmountByPaymentStatus(PaymentStatus.UNPAID);
    }

    private Invoice load(Long id) {
        return invoices.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", id));
    }

    private static boolean matches(Invoice invoice, String needle) {
        return invoice.getInvoiceNumber().toLowerCase(Locale.ROOT).contains(needle)
                || invoice.getClientName().toLowerCase(Locale.ROOT).contains(needle)
                || invoice.getSale().getSeller().getFullName().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** @param collected cash taken at hand-over; zero when the order was already paid */
    public record DeliveryResult(InvoiceResponse invoice, BigDecimal collected) {
    }
}
