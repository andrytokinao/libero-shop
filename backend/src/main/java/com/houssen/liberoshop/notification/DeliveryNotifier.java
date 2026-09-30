package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.service.OrderDeliveredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tells the seller that the order they rang up has been handed over.
 *
 * <p>The counterpart of {@link SaleNotifier}: the desk told the depot a sale was waiting, the
 * depot tells the desk it is done -- the customer who comes back to the counter has their goods,
 * and an unpaid order has now been paid in cash at the depot. Only the seller is told, the one
 * person who dealt with that customer; and not when they handed it over themselves.
 */
@Component
public class DeliveryNotifier {

    private final NotificationService notifications;

    public DeliveryNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent delivery) {
        if (delivery.sellerId() == null || delivery.sellerId().equals(delivery.agentId())) {
            return;
        }
        notifications.sendToUser(delivery.sellerId(), notificationOf(delivery));
    }

    static Notification notificationOf(OrderDeliveredEvent delivery) {
        boolean cash = delivery.collected() != null && delivery.collected().signum() > 0;
        String message = delivery.invoiceNumber() + " - " + delivery.clientName()
                + " - remise par " + delivery.agentName()
                + (cash ? ", " + amount(delivery.collected()) + " Ar encaisses au depot." : ".");
        return Notification.of(NotificationType.ORDER_DELIVERED, "Commande remise", message,
                new OrderDelivered(delivery.invoiceId(), delivery.invoiceNumber(), delivery.clientName(),
                        delivery.agentName(), delivery.collected()));
    }

    private static String amount(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** What a screen needs: the invoice, who handed it over, and the cash taken if any. */
    public record OrderDelivered(Long invoiceId,
                                 String invoiceNumber,
                                 String clientName,
                                 String agentName,
                                 BigDecimal collected) {
    }
}
