package com.houssen.liberoshop.desktop;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.net.BindException;

/**
 * Follows the server through its starts and stops, for the tray icon to show.
 *
 * <p>It outlives the Spring context on purpose: a restore, or the tray's own "restart", closes
 * the context and opens another one in the same process. The same monitor is handed to each new
 * {@code SpringApplication}, and keeps hold of whichever context is serving at the moment.
 *
 * <p>Knows nothing of AWT, so it can be tested on a machine without a screen.
 */
final class ServerMonitor implements ApplicationListener<ApplicationEvent> {

    enum State { STARTING, RUNNING, STOPPED, FAILED }

    /**
     * @param address where the shop is served, only while {@link State#RUNNING}
     * @param failure why the last start did not succeed, only while {@link State#FAILED}
     */
    record Status(State state, String address, String failure) {
    }

    private final Runnable onChange;

    private volatile Status status = new Status(State.STOPPED, null, null);
    private volatile ConfigurableApplicationContext context;

    ServerMonitor(Runnable onChange) {
        this.onChange = onChange;
    }

    Status status() {
        return status;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartingEvent) {
            update(new Status(State.STARTING, null, null));
        } else if (event instanceof ApplicationReadyEvent ready) {
            context = ready.getApplicationContext();
            update(new Status(State.RUNNING, address(context), null));
        } else if (event instanceof ApplicationFailedEvent failed) {
            failed(failed.getException());
        } else if (event instanceof ContextClosedEvent closed
                && closed.getApplicationContext() == context
                // A failed start closes its context too: the reason must stay on display.
                && status.state() != State.FAILED) {
            update(new Status(State.STOPPED, null, null));
        }
    }

    void failed(Throwable failure) {
        update(new Status(State.FAILED, null, rootMessage(failure)));
    }

    /** Closes the serving context, the database with it. Does nothing when it is already down. */
    void stop() {
        ConfigurableApplicationContext serving = context;
        if (serving != null && serving.isActive()) {
            serving.close();
        }
    }

    private void update(Status next) {
        status = next;
        onChange.run();
    }

    /** The port actually bound, which a random or overridden port makes differ from the default. */
    private static String address(ConfigurableApplicationContext context) {
        String port = context.getEnvironment().getProperty("local.server.port",
                context.getEnvironment().getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }

    /** Spring wraps the cause several levels deep; the innermost one is what the shop can act on. */
    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof BindException) {
            // By far the usual cause: the shortcut was double-clicked while the server was up.
            return "le port est déjà utilisé. Moussa Shop est peut-être déjà lancé.";
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
