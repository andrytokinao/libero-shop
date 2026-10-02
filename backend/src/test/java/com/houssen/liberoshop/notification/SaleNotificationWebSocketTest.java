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
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.service.RemittanceService;
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
    private InvoiceService invoiceService;
    @Autowired
    private RemittanceService remittanceService;
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
    private BlockingQueue<Map<String, Object>> listen(UserApp user) throws Exception {
        return subscribe(user, NotificationService.USER_PREFIX + NotificationService.USER_QUEUE);
    }

    private BlockingQueue<Map<String, Object>> listenTopic(UserApp user, String topic) throws Exception {
        return subscribe(user, NotificationService.TOPIC_PREFIX + "/" + topic);
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> subscribe(UserApp user, String destination) throws Exception {
        StompSession session = connect(tokenOf(user));
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
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
    @DisplayName("the depot queue follows a sale and its hand-over, on the seller's own screen too")
    void deliveryQueueFollowsSaleAndHandOver() throws Exception {
        // Seller and storekeeper in one: the phone open on the queue belongs to the seller.
        UserApp allRounder = account(RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        Product oil = products.save(Product.builder().name("Huile " + UUID.randomUUID())
                .price(new BigDecimal("9000")).stockQuantity(10).build());
        BlockingQueue<Map<String, Object>> queue = listenTopic(allRounder, OrderChangePublisher.TOPIC);

        InvoiceResponse invoice = saleService.checkout(new CreateSaleRequest("Rina",
                PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(oil.getId(), 1))), allRounder);

        Map<String, Object> added = queue.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(added, "the queue never heard of the sale");
        assertEquals(OrderChangePublisher.Change.ADDED, added.get("change"));
        assertEquals(List.of(invoice.invoiceNumber()), added.get("invoiceNumbers"));

        invoiceService.deliver(invoice.id(), allRounder);

        Map<String, Object> delivered = queue.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(delivered, "the queue never heard of the hand-over");
        assertEquals(OrderChangePublisher.Change.DELIVERED, delivered.get("change"));
        assertEquals(List.of(invoice.invoiceNumber()), delivered.get("invoiceNumbers"));
        // The order as it now stands, so the screens apply it without reloading.
        List<?> carried = (List<?>) delivered.get("invoices");
        assertEquals(1, carried.size());
        Map<?, ?> order = (Map<?, ?>) carried.getFirst();
        assertEquals(invoice.id().intValue(), ((Number) order.get("id")).intValue());
        assertEquals("DELIVERED", order.get("deliveryStatus"));
        assertTrue(order.get("invoiceDate") instanceof String, "dates go out as ISO text, as over REST");
        assertNull(delivered.get("remittance"));
    }

    @Test
    @DisplayName("a sale pushes the new stock of what it sold to every till")
    void saleBroadcastsTheNewStock() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp otherTill = account(RoleApp.CASHIER);
        Product flour = products.save(Product.builder().name("Farine " + UUID.randomUUID())
                .price(new BigDecimal("3000")).stockQuantity(10).build());
        BlockingQueue<Map<String, Object>> stock = listenTopic(otherTill, StockChangePublisher.TOPIC);

        saleService.checkout(new CreateSaleRequest("Rina", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(flour.getId(), 3))), cashier);

        Map<String, Object> change = stock.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(change, "the other till never heard the stock move");
        List<?> carried = (List<?>) change.get("products");
        assertEquals(1, carried.size());
        Map<?, ?> product = (Map<?, ?>) carried.getFirst();
        assertEquals(flour.getId().intValue(), ((Number) product.get("id")).intValue());
        assertEquals(7, ((Number) product.get("stockQuantity")).intValue());
    }

    @Test
    @DisplayName("the seller hears when the depot hands their order over, cash included")
    void handOverReachesTheSeller() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        Product sugar = products.save(Product.builder().name("Sucre " + UUID.randomUUID())
                .price(new BigDecimal("4000")).stockQuantity(10).build());
        InvoiceResponse invoice = saleService.checkout(new CreateSaleRequest("Hery",
                PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(sugar.getId(), 2))), cashier);

        BlockingQueue<Map<String, Object>> seller = listen(cashier);
        BlockingQueue<Map<String, Object>> depot = listen(storekeeper);

        invoiceService.deliver(invoice.id(), storekeeper);

        Map<String, Object> notification = seller.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(notification, "the seller was never told");
        assertEquals("ORDER_DELIVERED", notification.get("type"));
        assertTrue(((String) notification.get("message")).contains("8000 Ar encaisses"));
        assertEquals(invoice.invoiceNumber(), ((Map<?, ?>) notification.get("data")).get("invoiceNumber"));
        assertNull(depot.poll(1, TimeUnit.SECONDS), "the storekeeper is not told about their own hand-over");
    }

    @Test
    @DisplayName("the desk hears of cash brought by the depot, and the storekeeper of its confirmation")
    void remittanceReachesBothEnds() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        Product salt = products.save(Product.builder().name("Sel " + UUID.randomUUID())
                .price(new BigDecimal("1500")).stockQuantity(10).build());
        InvoiceResponse invoice = saleService.checkout(new CreateSaleRequest("Lova",
                PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(salt.getId(), 2))), cashier);
        invoiceService.deliver(invoice.id(), storekeeper);

        BlockingQueue<Map<String, Object>> desk = listen(cashier);
        BlockingQueue<Map<String, Object>> depot = listen(storekeeper);
        desk.clear();

        Long slipId = remittanceService.submit(storekeeper, List.of(invoice.id())).id();
        Map<String, Object> submitted = desk.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(submitted, "the desk was never told");
        assertEquals("REMITTANCE_SUBMITTED", submitted.get("type"));
        assertTrue(((String) submitted.get("message")).contains("3000 Ar"));

        remittanceService.confirm(slipId, cashier);
        Map<String, Object> confirmed = depot.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(confirmed, "the storekeeper was never told");
        assertEquals("REMITTANCE_CONFIRMED", confirmed.get("type"));
        assertEquals(List.of(invoice.invoiceNumber()), ((Map<?, ?>) confirmed.get("data")).get("invoiceNumbers"));
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
