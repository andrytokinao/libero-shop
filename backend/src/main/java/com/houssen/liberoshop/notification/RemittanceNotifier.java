package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.service.RemittanceRecordedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * The two ends of the depot cash hand-over, each told when the other has acted.
 *
 * <p>A slip brought to the desk goes to every connected cashier -- any of them may count and
 * confirm it -- but not to the storekeeper who brought it, should they also hold the till. A
 * confirmation goes to that storekeeper alone: it is their cash that has just been signed for.
 */
@Component
public class RemittanceNotifier {

    static final Set<RoleApp> DESK = Set.of(RoleApp.CASHIER);

    private final NotificationService notifications;

    public RemittanceNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRemittance(RemittanceRecordedEvent remittance) {
        if (remittance.confirmed()) {
            notifications.sendToUser(remittance.agentId(), confirmedOf(remittance));
        } else {
            notifications.sendToRoles(DESK, submittedOf(remittance), remittance.agentId());
        }
    }

    static Notification submittedOf(RemittanceRecordedEvent remittance) {
        String message = "V-" + remittance.remittanceId() + " de " + remittance.agentName() + " : "
                + amount(remittance.amount()) + " Ar (" + orders(remittance.invoiceNumbers())
                + "). Comptez l'argent puis confirmez la reception.";
        return Notification.of(NotificationType.REMITTANCE_SUBMITTED, "Versement a confirmer", message,
                dataOf(remittance));
    }

    static Notification confirmedOf(RemittanceRecordedEvent remittance) {
        String message = "V-" + remittance.remittanceId() + " : " + amount(remittance.amount())
                + " Ar recus a la caisse par " + remittance.cashierName() + ".";
        return Notification.of(NotificationType.REMITTANCE_CONFIRMED, "Versement confirme", message,
                dataOf(remittance));
    }

    private static RemittanceData dataOf(RemittanceRecordedEvent remittance) {
        return new RemittanceData(remittance.remittanceId(), remittance.amount(), remittance.invoiceNumbers(),
                remittance.agentName(), remittance.cashierName());
    }

    private static String orders(List<String> numbers) {
        return numbers.size() == 1 ? numbers.get(0) : numbers.size() + " commandes";
    }

    private static String amount(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** What a screen needs: the slip, its amount, its orders, and who is at each end. */
    public record RemittanceData(Long remittanceId,
                                 BigDecimal amount,
                                 List<String> invoiceNumbers,
                                 String agentName,
                                 String cashierName) {
    }
}
