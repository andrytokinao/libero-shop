package com.houssen.liberoshop.notification;

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
 * <p>A topic rather than a queue per account: orders are the whole shop's -- any signed-in
 * account may already list them all -- and a topic is what any signed-in screen may follow.
 *
 * <p>Each message carries the orders as they now stand, read back after the commit exactly as
 * {@code GET /api/invoices} serves them, and the slip for a cash change. The browser's stores
 * apply them in place: a hundred screens open on the queue cost one read here, not a hundred
 * reloads. Should that read fail, the message still goes out with the numbers alone, and the
 * screens fall back to reloading -- the REST API stays the source of truth.
 */
@Component
public class OrderChangePublisher {

    /** {@code /topic/orders} on the socket. */
    public static final String TOPIC = "orders";

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
        List<InvoiceResponse> current = List.of();
        CashRemittanceResponse slip = null;
        try {
            current = invoices.findAllById(invoiceIds);
            slip = remittanceId == null ? null : remittances.findById(remittanceId);
        } catch (RuntimeException e) {
            // The change is committed whatever happens here: the screens are told without the data.
            log.warn("Orders {} not read back for the {} message: {}", numbers, change, e.getMessage());
        }
        notifications.publish(TOPIC, new Change(change, numbers, current, slip));
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
