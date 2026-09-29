package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.service.SaleRecordedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Tells the depot that a sale is waiting.
 *
 * <p>The storekeepers are the ones a sale sets to work -- the goods have to be picked and handed
 * over -- and the depot manager watches what leaves the stock. The seller is left out: they
 * have just made the sale, and in a one-person shop they would otherwise be told about it by
 * their own screen.
 *
 * <p>Runs after the commit, so a checkout that fails and rolls back never announces a sale that
 * does not exist. A notification that cannot be pushed is logged by {@link NotificationService};
 * the sale stands either way.
 */
@Component
public class SaleNotifier {

    /** Who is told about a new sale. */
    static final Set<RoleApp> AUDIENCE = Set.of(RoleApp.DEPOT_AGENT, RoleApp.DEPOT_MANAGER);

    private final NotificationService notifications;

    public SaleNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleRecorded(SaleRecordedEvent sale) {
        notifications.sendToRoles(AUDIENCE, notificationOf(sale), sale.sellerId());
    }

    static Notification notificationOf(SaleRecordedEvent sale) {
        String settlement = sale.paymentStatus() == PaymentStatus.PAID
                ? "payee"
                : "a encaisser a la remise";
        String message = sale.invoiceNumber() + " - " + sale.clientName() + " - "
                + sale.units() + " article(s), " + amount(sale.totalAmount()) + " Ar, "
                + settlement + ". Vendu par " + sale.sellerName() + ".";
        return Notification.of(NotificationType.SALE_CREATED, "Nouvelle vente a preparer",
                message, SaleCreated.of(sale));
    }

    private static String amount(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * What a screen needs to act on the sale: the invoice to open, whether money is still to be
     * collected on hand-over.
     */
    public record SaleCreated(Long invoiceId,
                              String invoiceNumber,
                              String clientName,
                              BigDecimal totalAmount,
                              PaymentStatus paymentStatus,
                              int units,
                              String sellerName) {

        static SaleCreated of(SaleRecordedEvent sale) {
            return new SaleCreated(sale.invoiceId(), sale.invoiceNumber(), sale.clientName(),
                    sale.totalAmount(), sale.paymentStatus(), sale.units(), sale.sellerName());
        }
    }
}
