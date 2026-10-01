package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.service.OrderCancelledEvent;
import com.houssen.liberoshop.service.OrderDeliveredEvent;
import com.houssen.liberoshop.service.OrderPaidEvent;
import com.houssen.liberoshop.service.RemittanceRecordedEvent;
import com.houssen.liberoshop.service.SaleRecordedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Tells every screen showing orders that one of them has changed: a sale recorded, an order
 * handed over, its cash brought to the desk, that cash confirmed.
 *
 * <p>Apart from the notifiers on purpose: they tell <em>people</em> -- a toast, the bell -- and
 * choose whom; this one tells <em>screens</em>, and leaves nobody out. The seller's own phone,
 * open on the depot queue, must show the sale they just made at the desk; an order handed over by
 * one storekeeper must vanish from the others' screens before a second one walks to the shelves
 * for it; and the desk's invoice list must show the depot's cash arriving.
 *
 * <p>A topic rather than a queue per account: orders are the whole shop's, and a topic is what
 * any signed-in screen may follow. The payload is a cue, not the data -- screens reload from the
 * REST API, the one source of truth.
 */
@Component
public class OrderChangePublisher {

    /** {@code /topic/orders} on the socket. */
    public static final String TOPIC = "orders";

    private final NotificationService notifications;

    public OrderChangePublisher(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleRecorded(SaleRecordedEvent sale) {
        notifications.publish(TOPIC, new Change(Change.ADDED, List.of(sale.invoiceNumber())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent delivery) {
        notifications.publish(TOPIC, new Change(Change.DELIVERED, List.of(delivery.invoiceNumber())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent payment) {
        notifications.publish(TOPIC, new Change(Change.PAID, List.of(payment.invoiceNumber())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent cancellation) {
        notifications.publish(TOPIC, new Change(Change.CANCELLED, List.of(cancellation.invoiceNumber())));
    }

    /** One message per slip, however many orders it carries: each screen reloads once. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRemittance(RemittanceRecordedEvent remittance) {
        String change = remittance.confirmed() ? Change.CASH_CONFIRMED : Change.CASH_REMITTED;
        notifications.publish(TOPIC, new Change(change, remittance.invoiceNumbers()));
    }

    /**
     * @param change         what happened: one of the constants below
     * @param invoiceNumbers the orders it happened to
     */
    public record Change(String change, List<String> invoiceNumbers) {
        /** Recorded at the desk: waiting at the depot. */
        public static final String ADDED = "ADDED";
        /** Handed over to the customer. */
        public static final String DELIVERED = "DELIVERED";
        /** Its cash brought to the desk, not confirmed yet. */
        public static final String CASH_REMITTED = "CASH_REMITTED";
        /** Its cash confirmed in the till: the order is paid. */
        public static final String CASH_CONFIRMED = "CASH_CONFIRMED";
        /** Settled at the till. */
        public static final String PAID = "PAID";
        /** Cancelled while unpaid. */
        public static final String CANCELLED = "CANCELLED";
    }
}
