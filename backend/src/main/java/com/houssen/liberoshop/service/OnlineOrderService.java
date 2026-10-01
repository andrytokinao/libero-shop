package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.DiningTable;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.DiningTableRepository;
import com.houssen.liberoshop.repository.InvoiceRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.DiningTableResponse;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.PublicMenuResponse;
import com.houssen.liberoshop.web.dto.PublicOrderRequest;
import com.houssen.liberoshop.web.dto.PublicOrderResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ordering from the table: the customer scans the table's QR code, picks from the menu, sends.
 *
 * <p>The order is an ordinary unpaid sale from then on -- the waiter serves it and takes the
 * money or leaves it to the till, the cash goes up the usual trail. Two things differ, both
 * because the caller is anonymous:
 * <ul>
 *   <li>the seller is {@link #ONLINE_ACCOUNT}, an account that cannot sign in, so the reports
 *       tell these orders apart and nobody's figures are inflated by them;</li>
 *   <li>the table is known by an unguessable token only, and the endpoints refuse to work at all
 *       unless the shop switched online ordering on. A table may send one order every
 *       {@link #ORDER_INTERVAL}, of at most {@link #MAX_UNITS_PER_LINE} units an article: enough
 *       for a family, too little to empty the stock from a sofa.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OnlineOrderService {

    /** Login handle of the account online orders are recorded under. */
    public static final String ONLINE_ACCOUNT = "commande-en-ligne";
    static final Duration ORDER_INTERVAL = Duration.ofSeconds(30);
    static final int MAX_UNITS_PER_LINE = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DiningTableRepository tables;
    private final InvoiceRepository invoices;
    private final UserAppRepository users;
    private final CatalogService catalog;
    private final SaleService sales;
    private final ShopSettingsService settings;
    private final BusinessCalendar calendar;
    private final PasswordEncoder passwordEncoder;

    /** When each table last ordered. In memory: a restart forgetting it costs nothing. */
    private final Map<Long, LocalDateTime> lastOrderAt = new ConcurrentHashMap<>();

    public OnlineOrderService(DiningTableRepository tables, InvoiceRepository invoices, UserAppRepository users,
                              CatalogService catalog, SaleService sales, ShopSettingsService settings,
                              BusinessCalendar calendar, PasswordEncoder passwordEncoder) {
        this.tables = tables;
        this.invoices = invoices;
        this.users = users;
        this.catalog = catalog;
        this.sales = sales;
        this.settings = settings;
        this.calendar = calendar;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ the tables (admin)

    public List<DiningTableResponse> tables() {
        return tables.findAllByOrderByNameAsc().stream().map(DiningTableResponse::of).toList();
    }

    @Transactional
    public DiningTableResponse createTable(String name) {
        String trimmed = name.trim();
        if (tables.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessRuleException("TABLE_EXISTS", "La table \"" + trimmed + "\" existe deja.");
        }
        return DiningTableResponse.of(tables.save(DiningTable.builder().name(trimmed).token(newToken()).build()));
    }

    /** Withdraws the table's printed QR code: the old link stops working, a new one is issued. */
    @Transactional
    public DiningTableResponse regenerateToken(Long id) {
        DiningTable table = tables.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Table", id));
        table.setToken(newToken());
        return DiningTableResponse.of(table);
    }

    /** The orders it sent stay: they name the table in their client field, not by reference. */
    @Transactional
    public void deleteTable(Long id) {
        DiningTable table = tables.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Table", id));
        tables.delete(table);
        lastOrderAt.remove(id);
    }

    // ------------------------------------------------------------------ the customer (public)

    public PublicMenuResponse menu(String token) {
        DiningTable table = openTable(token);
        List<PublicMenuResponse.Item> items = catalog.findProducts(null, null, false).stream()
                .map(p -> new PublicMenuResponse.Item(p.id(), p.name(), p.price(),
                        p.category() == null ? null : p.category().name(), p.stockQuantity() > 0))
                .toList();
        return new PublicMenuResponse(table.getName(), items);
    }

    @Transactional
    public PublicOrderResponse order(String token, PublicOrderRequest request) {
        DiningTable table = openTable(token);
        for (CreateSaleRequest.Line line : request.lines()) {
            if (line.quantity() > MAX_UNITS_PER_LINE) {
                throw new BusinessRuleException("TOO_MANY_UNITS",
                        "Au plus " + MAX_UNITS_PER_LINE + " par article : demandez au serveur pour davantage.");
            }
        }
        LocalDateTime now = calendar.now();
        LocalDateTime previous = lastOrderAt.get(table.getId());
        if (previous != null && previous.plus(ORDER_INTERVAL).isAfter(now)) {
            throw new BusinessRuleException("ORDER_TOO_SOON",
                    "Votre commande precedente vient d'etre envoyee. Patientez quelques secondes.");
        }

        InvoiceResponse invoice = sales.checkout(new CreateSaleRequest(clientNameOf(table, request.clientName()),
                PaymentStatus.UNPAID, PaymentMethod.CASH, request.lines()), onlineAccount());
        lastOrderAt.put(table.getId(), now);
        return toPublic(invoice, table);
    }

    /** What the customer's phone polls to show "Servie": only the orders of that very table. */
    public PublicOrderResponse status(String token, String invoiceNumber) {
        DiningTable table = openTable(token);
        Invoice invoice = invoices.findByInvoiceNumber(invoiceNumber)
                .filter(found -> ONLINE_ACCOUNT.equals(found.getSale().getSeller().getUsername()))
                .filter(found -> belongsTo(found.getClientName(), table))
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable pour cette table."));
        return toPublic(InvoiceResponse.of(invoice), table);
    }

    // ------------------------------------------------------------------ internals

    /** The table of a live QR code, or a 404 that says nothing about why. */
    private DiningTable openTable(String token) {
        if (!settings.features().onlineOrdering()) {
            throw new ResourceNotFoundException("La commande en ligne n'est pas disponible.");
        }
        return tables.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ce QR code n'est plus valable : demandez au serveur."));
    }

    /** "Table 4", or "Table 4 · Rina" when the customer gave a name. */
    private static String clientNameOf(DiningTable table, String customer) {
        String name = customer == null ? "" : customer.trim();
        return name.isEmpty() ? table.getName() : table.getName() + " · " + name;
    }

    private static boolean belongsTo(String clientName, DiningTable table) {
        return clientName.equals(table.getName()) || clientName.startsWith(table.getName() + " · ");
    }

    /**
     * The account online orders are signed by, created the first time it is needed. Disabled: it
     * cannot sign in, and the password nobody knows is only there because the column requires one.
     */
    private UserApp onlineAccount() {
        return users.findByUsername(ONLINE_ACCOUNT).orElseGet(() -> users.save(UserApp.builder()
                .fullName("Commande en ligne")
                .username(ONLINE_ACCOUNT)
                .password(passwordEncoder.encode(newToken() + newToken()))
                .enabled(false)
                .roles(EnumSet.of(RoleApp.ORDER_TAKER))
                .build()));
    }

    private static PublicOrderResponse toPublic(InvoiceResponse invoice, DiningTable table) {
        String status = invoice.paymentStatus() == PaymentStatus.CANCELLED ? "CANCELLED"
                : invoice.deliveryStatus() == DeliveryStatus.DELIVERED ? "SERVED" : "RECEIVED";
        boolean paid = invoice.paymentStatus() != PaymentStatus.UNPAID
                && invoice.paymentStatus() != PaymentStatus.CANCELLED;
        List<PublicOrderResponse.Line> lines = invoice.sale().lines().stream()
                .map(line -> new PublicOrderResponse.Line(line.product().name(), line.quantity()))
                .toList();
        return new PublicOrderResponse(invoice.invoiceNumber(), table.getName(), invoice.sale().totalAmount(),
                status, paid, lines);
    }

    /** 16 random bytes, URL-safe: 22 characters nobody guesses. */
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
