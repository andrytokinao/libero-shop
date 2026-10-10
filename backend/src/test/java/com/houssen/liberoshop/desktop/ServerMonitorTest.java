package com.houssen.liberoshop.desktop;

import com.houssen.liberoshop.desktop.ServerMonitor.State;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockPropertySource;

import java.net.BindException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMonitorTest {

    private static final String[] NO_ARGS = new String[0];

    private final SpringApplication application = new SpringApplication(ServerMonitorTest.class);
    private final AtomicInteger changes = new AtomicInteger();
    private final ServerMonitor monitor = new ServerMonitor(changes::incrementAndGet);

    @Test
    void isStoppedUntilTheApplicationStarts() {
        assertThat(monitor.status().state()).isEqualTo(State.STOPPED);

        monitor.onApplicationEvent(new ApplicationStartingEvent(new DefaultBootstrapContext(), application, NO_ARGS));

        assertThat(monitor.status().state()).isEqualTo(State.STARTING);
        assertThat(changes).hasValue(1);
    }

    @Test
    void runsAtTheBoundPortOnceReady() {
        GenericApplicationContext context = contextOnPort("9123");

        monitor.onApplicationEvent(new ApplicationReadyEvent(application, NO_ARGS, context, Duration.ZERO));

        assertThat(monitor.status().state()).isEqualTo(State.RUNNING);
        assertThat(monitor.status().address()).isEqualTo("http://localhost:9123");
    }

    @Test
    void stopClosesTheServingContext() {
        GenericApplicationContext context = contextOnPort("8080");
        context.addApplicationListener(monitor);
        context.refresh();
        monitor.onApplicationEvent(new ApplicationReadyEvent(application, NO_ARGS, context, Duration.ZERO));

        monitor.stop();

        assertThat(context.isActive()).isFalse();
        assertThat(monitor.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void aFailedStartKeepsItsReasonWhenItsContextCloses() {
        GenericApplicationContext context = contextOnPort("8080");
        monitor.onApplicationEvent(new ApplicationReadyEvent(application, NO_ARGS, context, Duration.ZERO));

        monitor.onApplicationEvent(new ApplicationFailedEvent(application, NO_ARGS, context,
                new IllegalStateException("wrapper", new IllegalArgumentException("Port 8080 is already in use"))));
        monitor.onApplicationEvent(new ContextClosedEvent(context));

        assertThat(monitor.status().state()).isEqualTo(State.FAILED);
        assertThat(monitor.status().failure()).isEqualTo("Port 8080 is already in use");
    }

    @Test
    void saysThePortIsTakenWhenASecondInstanceIsLaunched() {
        monitor.failed(new IllegalStateException("Unable to start embedded Tomcat",
                new BindException("Address already in use: bind")));

        assertThat(monitor.status().failure()).contains("port est déjà utilisé");
    }

    @Test
    void ignoresTheClosingOfAContextThatIsNotServing() {
        GenericApplicationContext serving = contextOnPort("8080");
        monitor.onApplicationEvent(new ApplicationReadyEvent(application, NO_ARGS, serving, Duration.ZERO));

        monitor.onApplicationEvent(new ContextClosedEvent(new GenericApplicationContext()));

        assertThat(monitor.status().state()).isEqualTo(State.RUNNING);
    }

    private static GenericApplicationContext contextOnPort(String port) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MockPropertySource().withProperty("local.server.port", port));
        return context;
    }
}
