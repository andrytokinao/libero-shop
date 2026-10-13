package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.ShopFeatures;
import com.houssen.liberoshop.service.SaleRecordedEvent;
import com.houssen.liberoshop.service.ShopSettingsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.houssen.liberoshop.util.Quantities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Tells whoever a sale sets to work: the depot that it is waiting, the till that it is unpaid.
 *
 * <p>The storekeepers are the ones a sale sets to work -- the goods have to be picked and handed
 * over -- and the depot manager watches what leaves the stock. The cashiers are told of an order
 * left unpaid -- a waiter's, a receptionist's, a colleague's: its money is theirs to take, and
 * without a word the order would wait in "A encaisser" until someone looked. A sale paid at the
 * desk asks nothing of the till, and is not announced there.
 *
 * <p>The seller is left out of both: they have just made the sale, and in a one-person shop they
 * would otherwise be told about it by their own screen.
 *
 * <p>Runs after the commit, so a checkout that fails and rolls back never announces a sale that
 * does not exist. A notification that cannot be pushed is logged by {@link NotificationService};
 * the sale stands either way.
 */
@Component
public class SaleNotifier {

    /** Who is told a sale is waiting to be handed over. */
    static final Set<RoleApp> DEPOT = Set.of(RoleApp.DEPOT_AGENT, RoleApp.DEPOT_MANAGER);
    /** Who is told an order is waiting to be paid. */
    static final Set<RoleApp> DESK = Set.of(RoleApp.CASHIER);

    private final NotificationService notifications;
    private final ShopSettingsService settings;

    public SaleNotifier(NotificationService notifications, ShopSettingsService settings) {
        this.notifications = notifications;
        this.settings = settings;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleRecorded(SaleRecordedEvent sale) {
        ShopFeatures features = settings.features();
        // A counter hands the goods over with the sale: nobody is left to prepare anything.
        if (features.separateDelivery()) {
            notifications.sendToRoles(DEPOT, notificationOf(sale, features.payAtDepot()), sale.sellerId());
        }
        if (sale.paymentStatus() == PaymentStatus.UNPAID) {
            notifications.sendToRoles(DESK, toCollectOf(sale, features.payAtDepot()), sale.sellerId());
        }
    }

    static Notification notificationOf(SaleRecordedEvent sale, boolean payAtDepot) {
        String settlement = switch (sale.paymentStatus()) {
            case PAID -> "payee";
            // Paid to whoever took the order: nothing to collect on hand-over.
            case COLLECTED, REMITTED -> "payee a " + sale.sellerName();
            default -> payAtDepot ? "a encaisser a la remise" : "a regler a la caisse";
        };
        String message = sale.invoiceNumber() + " - " + sale.clientName() + " - "
                + Quantities.format(sale.units()) + " article(s), " + amount(sale.totalAmount()) + " Ar, "
                + settlement + ". Vendu par " + sale.sellerName() + ".";
        return Notification.of(NotificationType.SALE_CREATED, "Nouvelle vente a preparer",
                message, SaleCreated.of(sale));
    }

    /** For the till: an order somebody else took, and nobody has paid. */
    static Notification toCollectOf(SaleRecordedEvent sale, boolean payAtDepot) {
        // Where the depot takes money, the customer may still choose to pay at the desk.
        String where = payAtDepot ? "a la caisse ou a la remise" : "a la caisse";
        String message = sale.invoiceNumber() + " - " + sale.clientName() + " - "
                + amount(sale.totalAmount()) + " Ar a encaisser " + where
                + ". Pris par " + sale.sellerName() + ".";
        return Notification.of(NotificationType.ORDER_TO_COLLECT, "Nouvelle commande a encaisser",
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
                              BigDecimal units,
                              String sellerName) {

        static SaleCreated of(SaleRecordedEvent sale) {
            return new SaleCreated(sale.invoiceId(), sale.invoiceNumber(), sale.clientName(),
                    sale.totalAmount(), sale.paymentStatus(), sale.units(), sale.sellerName());
        }
    }
}
