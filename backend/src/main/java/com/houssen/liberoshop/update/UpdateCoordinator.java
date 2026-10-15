package com.houssen.liberoshop.update;

import com.houssen.liberoshop.notification.NotificationService;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Gates an update behind the agreement of the screens it would interrupt.
 *
 * <p>A till is never cut off unannounced: when an update is ready and confirmation is required,
 * every connected screen is asked, and the update is applied only once they have all agreed. If
 * one declines, or they do not all answer within the timeout, the round is dropped and the update
 * is left for next time. With confirmation off, or with nobody connected to interrupt, it is
 * applied at once after a short notice.
 *
 * <p>Applying runs off the caller's thread: a confirmation POST returns immediately, and the long
 * work (downloading, swapping, restarting) happens behind the {@code APPLYING} notice.
 */
@Component
public class UpdateCoordinator {

    private static final Logger log = LoggerFactory.getLogger(UpdateCoordinator.class);

    private final NotificationService notifications;
    private final ScheduledExecutorService worker;

    private Round round; // guarded by this

    public UpdateCoordinator(NotificationService notifications) {
        this(notifications, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "liberoshop-update-confirm");
            t.setDaemon(true);
            return t;
        }));
    }

    /** For tests: an executor they control. */
    UpdateCoordinator(NotificationService notifications, ScheduledExecutorService worker) {
        this.notifications = notifications;
        this.worker = worker;
    }

    public synchronized boolean inProgress() {
        return round != null;
    }

    /**
     * Requests an update.
     *
     * @param version             the version to be installed, for the screens' messages
     * @param requireConfirmation whether to wait for the connected screens to agree
     * @param timeout             how long to wait for them before dropping the round
     * @param apply               what to do once cleared -- downloads/swaps/restarts; run off-thread
     */
    public synchronized void request(String version, boolean requireConfirmation, Duration timeout, Runnable apply) {
        if (round != null) {
            throw new BusinessRuleException("UPDATE_IN_PROGRESS",
                    "Une mise a jour attend deja la confirmation des postes.");
        }
        Set<Long> audience = new LinkedHashSet<>(notifications.connectedUserIds());
        if (!requireConfirmation || audience.isEmpty()) {
            announceAndApply(version, apply);
            return;
        }
        ScheduledFuture<?> timeoutTask = worker.schedule(
                () -> onTimeout(version), Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        round = new Round(version, audience, new LinkedHashSet<>(), apply, timeoutTask);
        notifications.publish(MaintenanceNotice.TOPIC, new MaintenanceNotice(
                MaintenanceNotice.Kind.CONFIRM_REQUEST, version,
                "Mise a jour vers la version " + version + " prete. Confirmez que ce poste peut etre coupe."));
        log.info("Confirmation de mise a jour {} demandee a {} poste(s).", version, audience.size());
    }

    /** A screen agrees to be cut off. When they all have, the update goes. */
    public synchronized void confirm(long userId) {
        if (round == null) {
            return;
        }
        round.agreed().add(userId);
        Set<Long> connected = notifications.connectedUserIds();
        boolean allAgreed = round.audience().stream()
                .filter(connected::contains)
                .allMatch(round.agreed()::contains);
        if (allAgreed) {
            Round ready = take();
            announceAndApply(ready.version(), ready.apply());
        }
    }

    /** A screen refuses the cut: the round is dropped and the update left for next time. */
    public synchronized void decline(long userId) {
        if (round == null) {
            return;
        }
        abort("Un poste a refuse la coupure. Mise a jour reportee.");
    }

    private synchronized void onTimeout(String version) {
        if (round == null || !round.version().equals(version)) {
            return;
        }
        abort("Les postes n'ont pas tous confirme a temps. Mise a jour reportee.");
    }

    private void announceAndApply(String version, Runnable apply) {
        notifications.publish(MaintenanceNotice.TOPIC, new MaintenanceNotice(
                MaintenanceNotice.Kind.APPLYING, version,
                "Mise a jour en cours. Le serveur va redemarrer, ne fermez pas cette page."));
        worker.submit(() -> {
            try {
                apply.run();
            } catch (RuntimeException e) {
                log.error("Echec de l'application de la mise a jour {}.", version, e);
                notifications.publish(MaintenanceNotice.TOPIC, new MaintenanceNotice(
                        MaintenanceNotice.Kind.CANCELLED, version,
                        "La mise a jour a echoue : " + e.getMessage()));
            }
        });
    }

    private void abort(String message) {
        Round dropped = take();
        notifications.publish(MaintenanceNotice.TOPIC, new MaintenanceNotice(
                MaintenanceNotice.Kind.CANCELLED, dropped == null ? null : dropped.version(), message));
        log.info("Mise a jour annulee : {}", message);
    }

    private Round take() {
        Round current = round;
        round = null;
        if (current != null) {
            current.timeout().cancel(false);
        }
        return current;
    }

    private record Round(String version, Set<Long> audience, Set<Long> agreed, Runnable apply,
                         ScheduledFuture<?> timeout) {
    }
}
