package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.service.InvoiceAccess;
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.service.OrderCancelledEvent;
import com.houssen.liberoshop.service.OrderDeliveredEvent;
import com.houssen.liberoshop.service.OrderPaidEvent;
import com.houssen.liberoshop.service.OrderHandlingChangedEvent;
import com.houssen.liberoshop.service.OrderPrintedEvent;
import com.houssen.liberoshop.service.RemittanceRecordedEvent;
import com.houssen.liberoshop.service.RemittanceService;
import com.houssen.liberoshop.service.SaleRecordedEvent;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tells every screen showing orders that one of them has changed: a sale recorded, an order
 * handed over, paid, cancelled or printed, its cash brought to the desk, that cash confirmed.
 *
 * <p>Apart from the notifiers on purpose: they tell <em>people</em> -- a toast, the bell -- and
 * choose whom; this one tells <em>screens</em>, and leaves nobody out. The seller's own phone,
 * open on the depot queue, must show the sale they just made at the desk; an order handed over by
 * one storekeeper must vanish from the others' screens before a second one walks to the shelves
 * for it; and the desk's invoice list must show the depot's cash arriving.
 *
 * <p>A queue per account rather than a topic: a topic hands its messages to whoever subscribes,
 * and not every account reads every order -- an order taker reads theirs, the till and the depot
 * the day's and the open ones ({@code InvoiceAccess}). So each connected account is sent its
 * own share, decided on the account as it is now, not as it was when it connected.
 *
 * <p>Each message carries the orders as they now stand, read back after the commit exactly as
 * {@code GET /api/invoices} serves them, and the slip for a cash change. The browser's stores
 * apply them in place: a hundred screens open on the queue cost one read here, not a hundred
 * reloads. Should that read fail, or should an order have left what the account reads, the
 * message goes out empty and the screens fall back to reloading -- the REST API stays the
 * source of truth.
 */
@Component
public class OrderChangePublisher {

    /** {@code /user/queue/orders} on the socket: each account's own share of the changes. */
    public static final String QUEUE = "orders";

    private static final Logger log = LoggerFactory.getLogger(OrderChangePublisher.class);

    private final NotificationService notifications;
    private final InvoiceService invoices;
    private final RemittanceService remittances;

    public OrderChangePublisher(NotificationService notifications, InvoiceService invoices,
                                RemittanceService remittances) {
        this.notifications = notifications;
        this.invoices = invoices;
        this.remittances = remittances;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleRecorded(SaleRecordedEvent sale) {
        publish(Change.ADDED, List.of(sale.invoiceNumber()), List.of(sale.invoiceId()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent delivery) {
        publish(Change.DELIVERED, List.of(delivery.invoiceNumber()), List.of(delivery.invoiceId()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent payment) {
        publish(Change.PAID, List.of(payment.invoiceNumber()), List.of(payment.invoiceId()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent cancellation) {
        publish(Change.CANCELLED, List.of(cancellation.invoiceNumber()), List.of(cancellation.invoiceId()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHandlingChanged(OrderHandlingChangedEvent handling) {
        publish(Change.HANDLING, List.of(handling.invoiceNumber()), List.of(handling.invoiceId()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPrinted(OrderPrintedEvent printed) {
        publish(Change.PRINTED, List.of(printed.invoiceNumber()), List.of(printed.invoiceId()), null);
    }

    /** One message per slip, however many orders it carries. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRemittance(RemittanceRecordedEvent remittance) {
        String change = remittance.confirmed() ? Change.CASH_CONFIRMED : Change.CASH_REMITTED;
        publish(change, remittance.invoiceNumbers(), remittance.invoiceIds(), remittance.remittanceId());
    }

    private void publish(String change, List<String> numbers, List<Long> invoiceIds, Long remittanceId) {
        Set<Long> listening = notifications.connectedUserIds();
        if (listening.isEmpty()) {
            return;
        }
        Map<Long, InvoiceService.Share> shares;
        CashRemittanceResponse slip;
        try {
            shares = invoices.sharesOf(invoiceIds, listening);
            slip = remittanceId == null ? null : remittances.findById(remittanceId);
        } catch (RuntimeException e) {
            // The change is committed whatever happens here: the screens are told to reload, and
            // the REST API gives each of them its share.
            log.warn("Orders {} not read back for the {} message: {}", numbers, change, e.getMessage());
            listening.forEach(userId -> notifications.sendToUserQueue(userId, QUEUE, Change.reload(change)));
            return;
        }
        shares.forEach((userId, share) -> {
            Change message = messageFor(userId, share, change, slip);
            if (message != null) {
                notifications.sendToUserQueue(userId, QUEUE, message);
            }
        });
    }

    /**
     * What one account is told, or null for nothing.
     *
     * <p>Whoever reads everything gets everything. An order taker gets the orders that are
     * theirs, and hears nothing of the others. The till and the depot read the orders of the day
     * and the open ones, so an order held back from them may be one that has just left that
     * set -- an old credit settled at another till: they are told to reload rather than left
     * showing it as it was.
     */
    private static Change messageFor(Long userId, InvoiceService.Share share, String change,
                                     CashRemittanceResponse slip) {
        if (!share.complete() && share.scope() == InvoiceAccess.Scope.CURRENT) {
            return Change.reload(change);
        }
        if (share.invoices().isEmpty()) {
            return null;
        }
        boolean readsSlip = slip != null
                && (share.readsEverySlip() || userId.equals(slip.submittedBy().id()));
        return new Change(change,
                share.invoices().stream().map(InvoiceResponse::invoiceNumber).toList(),
                share.invoices(),
                readsSlip ? slip : null);
    }

    /**
     * @param change         what happened: one of the constants below
     * @param invoiceNumbers the orders it happened to
     * @param invoices       those orders as they now stand; empty when they could not be read
     *                       back, in which case a screen reloads instead
     * @param remittance     the slip, for {@link #CASH_REMITTED} and {@link #CASH_CONFIRMED}; null
     *                       otherwise
     */
    public record Change(String change,
                         List<String> invoiceNumbers,
                         List<InvoiceResponse> invoices,
                         CashRemittanceResponse remittance) {

        /** Something of this kind happened, and the screen is to ask the REST API what. */
        static Change reload(String change) {
            return new Change(change, List.of(), List.of(), null);
        }

        /** Recorded at the desk: waiting at the depot. */
        public static final String ADDED = "ADDED";
        /** Handed over to the customer. */
        public static final String DELIVERED = "DELIVERED";
        /** Its cash brought to the desk, not confirmed yet. */
        public static final String CASH_REMITTED = "CASH_REMITTED";
        /** Its cash confirmed in the till: the order is paid. */
        public static final String CASH_CONFIRMED = "CASH_CONFIRMED";
        /** Settled at the till, or its cash taken by an order taker. */
        public static final String PAID = "PAID";
        /** Cancelled while unpaid. */
        public static final String CANCELLED = "CANCELLED";
        /** Sent to the printer. */
        public static final String PRINTED = "PRINTED";
        /** Taken on by someone ("je m'en occupe"), or given back to the queue. */
        public static final String HANDLING = "HANDLING";
    }
}
