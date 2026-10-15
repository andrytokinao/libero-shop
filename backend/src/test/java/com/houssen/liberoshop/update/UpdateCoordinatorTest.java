package com.houssen.liberoshop.update;

import com.houssen.liberoshop.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateCoordinatorTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final UpdateCoordinator coordinator = new UpdateCoordinator(notifications, worker);
    private final List<MaintenanceNotice> published = new ArrayList<>();

    UpdateCoordinatorTest() {
        doAnswer(invocation -> {
            published.add(invocation.getArgument(1));
            return null;
        }).when(notifications).publish(eq(MaintenanceNotice.TOPIC), any(MaintenanceNotice.class));
    }

    @AfterEach
    void tearDown() {
        worker.shutdownNow();
    }

    @Test
    void appliesImmediatelyWhenNobodyIsConnected() throws Exception {
        when(notifications.connectedUserIds()).thenReturn(Set.of());
        CountDownLatch applied = new CountDownLatch(1);

        coordinator.request("1.3.0", true, Duration.ofSeconds(5), applied::countDown);

        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(kinds()).containsExactly(MaintenanceNotice.Kind.APPLYING);
    }

    @Test
    void appliesImmediatelyWhenConfirmationIsOff() throws Exception {
        when(notifications.connectedUserIds()).thenReturn(Set.of(1L, 2L));
        CountDownLatch applied = new CountDownLatch(1);

        coordinator.request("1.3.0", false, Duration.ofSeconds(5), applied::countDown);

        assertThat(applied.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void waitsForEveryConnectedScreenBeforeApplying() throws Exception {
        when(notifications.connectedUserIds()).thenReturn(Set.of(1L, 2L));
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        coordinator.request("1.3.0", true, Duration.ofSeconds(5), () -> {
            applied.incrementAndGet();
            done.countDown();
        });
        assertThat(kinds()).containsExactly(MaintenanceNotice.Kind.CONFIRM_REQUEST);
        assertThat(coordinator.inProgress()).isTrue();

        coordinator.confirm(1L);
        assertThat(applied.get()).isZero();

        coordinator.confirm(2L);
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(applied.get()).isEqualTo(1);
        assertThat(kinds()).containsExactly(MaintenanceNotice.Kind.CONFIRM_REQUEST, MaintenanceNotice.Kind.APPLYING);
        assertThat(coordinator.inProgress()).isFalse();
    }

    @Test
    void aDeclineDropsTheRound() {
        when(notifications.connectedUserIds()).thenReturn(Set.of(1L, 2L));
        AtomicInteger applied = new AtomicInteger();

        coordinator.request("1.3.0", true, Duration.ofSeconds(5), applied::incrementAndGet);
        coordinator.decline(1L);

        assertThat(applied.get()).isZero();
        assertThat(coordinator.inProgress()).isFalse();
        assertThat(kinds()).containsExactly(MaintenanceNotice.Kind.CONFIRM_REQUEST, MaintenanceNotice.Kind.CANCELLED);
    }

    @Test
    void droppedWhenNotEveryoneConfirmsInTime() throws Exception {
        when(notifications.connectedUserIds()).thenReturn(Set.of(1L, 2L));
        AtomicInteger applied = new AtomicInteger();

        coordinator.request("1.3.0", true, Duration.ofMillis(200), applied::incrementAndGet);
        coordinator.confirm(1L);

        Thread.sleep(600);
        assertThat(applied.get()).isZero();
        assertThat(coordinator.inProgress()).isFalse();
        assertThat(kinds()).contains(MaintenanceNotice.Kind.CANCELLED);
    }

    private List<MaintenanceNotice.Kind> kinds() {
        return published.stream().map(MaintenanceNotice::kind).toList();
    }
}
