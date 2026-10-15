package com.houssen.liberoshop.update;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the automatic update check once a day, at the configured time, when the channel is on.
 *
 * <p>Deliberately its own tiny scheduler rather than Spring's {@code @Scheduled}: it reads the
 * time from configuration, turns itself off when the channel is disabled or has no URL, and adds
 * no application-wide scheduling. Each run reschedules the next, so there is never more than one
 * pending, and a failing check (server unreachable) is logged and swallowed -- it must not stop
 * tomorrow's.
 *
 * <p>Finding an update does not apply it directly: it goes through {@link UpdateCoordinator}, which
 * asks the connected screens first when confirmation is required.
 */
@Component
public class UpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(UpdateScheduler.class);

    private final UpdateProperties properties;
    private final UpdateService updates;
    private final UpdateInstaller installer;
    private final UpdateCoordinator coordinator;
    private ScheduledExecutorService scheduler;

    public UpdateScheduler(UpdateProperties properties, UpdateService updates,
                           UpdateInstaller installer, UpdateCoordinator coordinator) {
        this.properties = properties;
        this.updates = updates;
        this.installer = installer;
        this.coordinator = coordinator;
    }

    @PostConstruct
    void start() {
        if (!properties.automaticEnabled()) {
            log.info("Mise a jour automatique desactivee.");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "liberoshop-update-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduleNext();
        log.info("Mise a jour automatique activee : verification chaque jour a {}.", properties.checkTime());
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void scheduleNext() {
        long delay = Math.max(1, millisUntilNext(properties.checkTime()));
        scheduler.schedule(this::run, delay, TimeUnit.MILLISECONDS);
    }

    private void run() {
        try {
            check();
        } catch (RuntimeException e) {
            log.warn("Verification automatique de mise a jour echouee : {}", e.getMessage());
        } finally {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduleNext();
            }
        }
    }

    private void check() {
        if (!properties.automaticEnabled() || !installer.canApply() || coordinator.inProgress()) {
            return;
        }
        UpdateCheckResponse check = updates.checkOnline();
        if (!check.available()) {
            log.info("Verification automatique : deja a jour (version {}).", check.currentVersion());
            return;
        }
        log.info("Verification automatique : version {} disponible.", check.latestVersion());
        coordinator.request(check.latestVersion(), properties.requireConfirmation(),
                properties.confirmationTimeout(), updates::applyOnline);
    }

    /** Milliseconds from now to the next occurrence of {@code time}, today or tomorrow. */
    static long millisUntilNext(LocalTime time) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }
}
