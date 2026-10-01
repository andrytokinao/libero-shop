package com.houssen.liberoshop.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Restarts the application in the same process -- what a restore needs, since the database file
 * can only be replaced while nothing has it open.
 *
 * <p>The shop has no service manager to restart the server for it, so the application does it:
 * the current context is closed (the database with it), then started again with the arguments it
 * was launched with, which runs {@link StartupDatabaseGuard} on the way up.
 *
 * <p>Only possible when the application was started from {@code main}, which hands over how to
 * start it again. A test context has no such thing, and a restart is then refused, not attempted.
 */
@Component
public class ApplicationRestarter {

    private static final Logger log = LoggerFactory.getLogger(ApplicationRestarter.class);

    /** Lets the HTTP answer reach the browser before the server goes down. */
    private static final long GRACE_MILLIS = 1500;

    private static volatile Function<String[], ConfigurableApplicationContext> starter;
    private static volatile String[] arguments = new String[0];

    private final ConfigurableApplicationContext context;

    public ApplicationRestarter(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /** Called by {@code main}: how to start the application, and with which arguments. */
    public static void remember(Function<String[], ConfigurableApplicationContext> start, String[] args) {
        starter = start;
        arguments = args.clone();
    }

    public boolean canRestart() {
        return starter != null;
    }

    public void restartSoon() {
        if (!canRestart()) {
            throw new IllegalStateException("Redemarrage automatique indisponible : redemarrez le serveur.");
        }
        Thread restart = new Thread(() -> {
            try {
                Thread.sleep(GRACE_MILLIS);
                log.warn("Redemarrage de l'application.");
                context.close();
                starter.apply(arguments);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "liberoshop-restart");
        restart.setDaemon(false);
        restart.start();
    }
}
