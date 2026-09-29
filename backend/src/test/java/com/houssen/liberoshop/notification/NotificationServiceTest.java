package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Who a notification reaches. Every mistake here is silent: a storekeeper who is never told, or
 * a cashier who is told what is not theirs to see.
 */
class NotificationServiceTest {

    private SimpMessagingTemplate messaging;
    private SimpUserRegistry registry;
    private NotificationService service;
    private final Set<SimpUser> online = new HashSet<>();

    private final Notification hello = Notification.of(NotificationType.SALE_CREATED, "t", "m", null);

    @BeforeEach
    void setUp() {
        messaging = Mockito.mock(SimpMessagingTemplate.class);
        registry = Mockito.mock(SimpUserRegistry.class);
        Mockito.when(registry.getUsers()).thenAnswer(call -> Set.copyOf(online));
        service = new NotificationService(messaging, registry);
    }

    private void connected(long userId, RoleApp... roles) {
        SimpUser user = Mockito.mock(SimpUser.class);
        Mockito.when(user.getPrincipal()).thenReturn(new SocketPrincipal(userId, Set.of(roles)));
        online.add(user);
    }

    private void verifySentTo(long userId, int times) {
        Mockito.verify(messaging, Mockito.times(times)).convertAndSendToUser(
                String.valueOf(userId), NotificationService.USER_QUEUE, hello);
    }

    @Test
    @DisplayName("addresses one account by its id, on its own queue")
    void sendToUser() {
        service.sendToUser(7L, hello);

        verifySentTo(7, 1);
    }

    @Test
    @DisplayName("sends once per account, however often it is listed")
    void sendToUsersDeduplicates() {
        service.sendToUsers(List.of(7L, 8L, 7L), hello);

        verifySentTo(7, 1);
        verifySentTo(8, 1);
    }

    @Test
    @DisplayName("reaches the connected holders of a role, once each, and nobody else")
    void sendToRoles() {
        connected(1, RoleApp.DEPOT_AGENT);
        connected(2, RoleApp.DEPOT_AGENT, RoleApp.DEPOT_MANAGER);
        connected(3, RoleApp.CASHIER);

        int reached = service.sendToRoles(Set.of(RoleApp.DEPOT_AGENT, RoleApp.DEPOT_MANAGER), hello, null);

        assertEquals(2, reached);
        verifySentTo(1, 1);
        verifySentTo(2, 1);
        verifySentTo(3, 0);
    }

    @Test
    @DisplayName("leaves out the account whose action caused the message")
    void sendToRolesExcept() {
        connected(1, RoleApp.DEPOT_AGENT);
        connected(4, RoleApp.CASHIER, RoleApp.DEPOT_AGENT);

        int reached = service.sendToRoles(Set.of(RoleApp.DEPOT_AGENT), hello, 4L);

        assertEquals(1, reached);
        verifySentTo(4, 0);
    }

    @Test
    @DisplayName("a push that fails is logged, never thrown back at the sale")
    void failureIsSwallowed() {
        Mockito.doThrow(new MessageDeliveryException("broker down"))
                .when(messaging).convertAndSendToUser(ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(), ArgumentMatchers.any(Object.class));

        assertDoesNotThrow(() -> service.sendToUser(7L, hello));
    }

    @Test
    @DisplayName("publishes under the topic prefix, and refuses a name that would escape it")
    void publish() {
        service.publish("stock", Map.of("x", 1));

        Mockito.verify(messaging).convertAndSend("/topic/stock", (Object) Map.of("x", 1));
        assertThrows(IllegalArgumentException.class, () -> service.publish("../queue/x", 1));
        assertThrows(IllegalArgumentException.class, () -> service.publish("*", 1));
    }
}
