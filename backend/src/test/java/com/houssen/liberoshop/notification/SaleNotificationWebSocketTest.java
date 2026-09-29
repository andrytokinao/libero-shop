package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.security.AppUserDetails;
import com.houssen.liberoshop.security.JwtService;
import com.houssen.liberoshop.service.SaleService;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole path, as a browser would live it: a real WebSocket on a real port, a real STOMP
 * session opened with a real token, and a real checkout committing a sale.
 *
 * <p>Mocks would not do here. What is being checked is the wiring between four things that are
 * each correct alone -- the handshake left open by the HTTP security, the token checked on
 * CONNECT, the user-destination routing by account id, and the listener firing after commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleNotificationWebSocketTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private UserAppRepository users;
    @Autowired
    private ProductRepository products;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private SimpUserRegistry registry;

    private WebSocketStompClient client;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        client.stop();
    }

    // ------------------------------------------------------------------ fixtures

    private UserApp account(RoleApp... roles) {
        String handle = "u" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder()
                .fullName("Compte " + handle)
                .username(handle)
                .password("x")
                .enabled(true)
                .roles(EnumSet.copyOf(List.of(roles)))
                .build());
    }

    private String tokenOf(UserApp user) {
        return jwtService.issue(new AppUserDetails(user)).value();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        StompSession session = client
                .connectAsync("ws://localhost:" + port + WebSocketConfiguration.ENDPOINT,
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
                        })
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    /**
     * Opens a session for the account, subscribes to its queue, and waits until the broker
     * knows. The in-memory broker sends no receipt for a subscription, so its own registry is
     * asked instead -- which is also exactly the condition for a message to be routed.
     */
    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> listen(UserApp user) throws Exception {
        StompSession session = connect(tokenOf(user));
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        String destination = NotificationService.USER_PREFIX + NotificationService.USER_QUEUE;
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (registry.findSubscriptions(subscription -> destination.equals(subscription.getDestination())
                && String.valueOf(user.getId()).equals(subscription.getSession().getUser().getName()))
                .isEmpty()) {
            assertTrue(System.nanoTime() < deadline, "subscription never registered");
            Thread.onSpinWait();
        }
        return received;
    }

    // --------------------------------------------------------------------- tests

    @Test
    @DisplayName("a sale reaches the depot staff connected, and not the counter")
    void saleReachesTheDepot() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        UserApp otherCashier = account(RoleApp.CASHIER);
        Product rice = products.save(Product.builder().name("Riz " + UUID.randomUUID())
                .price(new BigDecimal("12500")).stockQuantity(40).build());

        BlockingQueue<Map<String, Object>> depot = listen(storekeeper);
        BlockingQueue<Map<String, Object>> seller = listen(cashier);
        BlockingQueue<Map<String, Object>> counter = listen(otherCashier);

        InvoiceResponse invoice = saleService.checkout(new CreateSaleRequest("Hotely Vaha",
                PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(rice.getId(), 2))), cashier);

        Map<String, Object> notification = depot.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(notification, "the storekeeper was never told");
        assertEquals("SALE_CREATED", notification.get("type"));
        assertTrue(((String) notification.get("message")).contains("a encaisser a la remise"));
        Map<?, ?> data = (Map<?, ?>) notification.get("data");
        assertEquals(invoice.invoiceNumber(), data.get("invoiceNumber"));
        assertEquals(2, ((Number) data.get("units")).intValue());

        assertNull(seller.poll(1, TimeUnit.SECONDS), "the seller is not told about their own sale");
        assertNull(counter.poll(1, TimeUnit.SECONDS), "the counter is not the depot's audience");
    }

    @Test
    @DisplayName("sendToUser reaches that account alone, by its id")
    void sendToUserById() throws Exception {
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        UserApp nadia = account(RoleApp.DEPOT_AGENT);
        BlockingQueue<Map<String, Object>> josephScreen = listen(joseph);
        BlockingQueue<Map<String, Object>> nadiaScreen = listen(nadia);

        notifications.sendToUser(joseph.getId(),
                Notification.of(NotificationType.SALE_CREATED, "Pour Joseph", "seul", null));

        Map<String, Object> notification = josephScreen.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(notification);
        assertEquals("Pour Joseph", notification.get("title"));
        assertNull(nadiaScreen.poll(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("a forged token never gets a session")
    void forgedTokenRefused() {
        assertThrows(Exception.class, () -> connect("not-a-token"));
    }
}
