package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.CashRemittance;
import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.SaleLine;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.InvoiceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.BusinessCalendar;
import com.houssen.liberoshop.service.InvoiceNumbering;
import com.houssen.liberoshop.service.PurchaseCosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.catalog.Catalog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;


@Component
@EnableConfigurationProperties(DemoDataProperties.class)
@ConditionalOnProperty(prefix = "liberoshop.demo-data", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserAppRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final CashRemittanceRepository remittances;
    private final StockMovementRepository movements;
    private final PasswordEncoder passwordEncoder;
    private final DemoDataProperties properties;
    private final BusinessCalendar calendar;

    public DataInitializer(UserAppRepository users, CategoryRepository categories, ProductRepository products,
                           SupplierRepository suppliers, SaleRepository sales, InvoiceRepository invoices,
                           PaymentRepository payments, CashRemittanceRepository remittances,
                           StockMovementRepository movements, PasswordEncoder passwordEncoder,
                           DemoDataProperties properties, BusinessCalendar calendar) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.suppliers = suppliers;
        this.sales = sales;
        this.invoices = invoices;
        this.payments = payments;
        this.remittances = remittances;
        this.movements = movements;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.calendar = calendar;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.debug("Base deja initialisee, aucun jeu de donnees insere.");
            return;
        }

        log.info("Base vide : insertion du jeu de donnees initial.");
        Accounts accounts = seedAccounts();
        Catalog catalog = seedCatalog();
        seedSupplies(catalog, accounts.nadia());
        seedTrading(catalog, accounts);

        log.info("Jeu de donnees initial insere : {} utilisateurs, {} produits, {} factures.",
                users.count(), products.count(), invoices.count());
        if (properties.usesDefaultPassword()) {
            log.warn("Les comptes initiaux utilisent le mot de passe par defaut '{}'. "
                            + "Definissez liberoshop.demo-data.password avant toute mise en production.",
                    DemoDataProperties.DEFAULT_PASSWORD);
        }
    }

    // ------------------------------------------------------------------ accounts

    private Accounts seedAccounts() {
        String hash = passwordEncoder.encode(properties.password());
        UserApp fatima = user("Fatima Randria", "fatima", hash, RoleApp.CASHIER);
        UserApp hary = user("Hary Rakoto", "hary", hash, RoleApp.CASHIER);
        UserApp joseph = user("Joseph Andrianina", "joseph", hash, RoleApp.DEPOT_AGENT);
        UserApp nadia = user("Nadia Rasolofo", "nadia", hash, RoleApp.DEPOT_MANAGER);
        UserApp mparany = user("Mparany Solofo", "mparany", hash, RoleApp.SUPER_ADMIN);
        user("Soa Ravelo", "soa", hash, RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        return new Accounts(fatima, hary, joseph, nadia, mparany);
    }

    private UserApp user(String fullName, String username, String passwordHash, RoleApp... roles) {
        return users.save(UserApp.builder()
                .fullName(fullName)
                .username(username)
                .password(passwordHash)
                .enabled(true)
                .roles(EnumSet.copyOf(List.of(roles)))
                .build());
    }

    // ------------------------------------------------------------------- catalog

    private Catalog seedCatalog() {
        Category epicerie = category("Epicerie");
        Category boissons = category("Boissons");
        Category hygiene = category("Hygiene");
        Category laitiers = category("Produits laitiers");

        List<Product> list = List.of(
                product("Riz 5kg", 12500, 40, "6001001000015", epicerie),
                product("Huile 1L", 6800, 25, "6001001000022", epicerie),
                product("Sucre 1kg", 3200, 8, "6001001000039", epicerie),
                product("Farine 1kg", 2800, 60, "6001001000046", epicerie),
                product("Lait en poudre 400g", 9500, 15, "6001002000053", laitiers),
                product("Savon de Marseille", 1500, 100, "6001003000060", hygiene),
                product("Eau minerale 1.5L", 1200, 6, "6001004000077", boissons),
                product("Pates 500g", 2100, 50, "6001001000084", epicerie),
                product("Cafe moulu 250g", 7400, 18, "6001001000091", epicerie),
                product("Yaourt nature 4x125g", 5200, 9, "6001002000107", laitiers),
                product("Dentifrice 75ml", 4300, 32, "6001003000114", hygiene),
                product("Jus d'orange 1L", 4800, 4, "6001004000121", boissons));

        List<Supplier> supplierList = List.of(
                supplier("Grossiste Analakely", "034 12 345 67", "Riz, Sucre, Farine, Pates"),
                supplier("Distri Lait SARL", "032 55 987 21", "Lait en poudre, yaourts, beurre"),
                supplier("Aqua Plus", "033 44 221 09", "Eau minerale, jus, boissons gazeuses"),
                supplier("Hygiene Mada", "038 77 654 32", "Savons, dentifrices, detergents"));

        return new Catalog(list, supplierList);
    }

    private Category category(String name) {
        return categories.save(Category.builder().name(name).build());
    }

    /**
     * A reference bought at about three quarters of its shelf price, rounded to the hundred
     * Ariary -- a grocery's usual mark-up, so the margin screens have plausible figures.
     */
    private Product product(String name, long price, int stock, String barcode, Category category) {
        return products.save(Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(price))
                .averageCost(PurchaseCosting.scaled(BigDecimal.valueOf(Math.round(price * 0.75 / 100) * 100)))
                .stockQuantity(stock)
                .barcode(barcode)
                .category(category)
                .build());
    }

    private Supplier supplier(String name, String contact, String suppliedProducts) {
        return suppliers.save(Supplier.builder()
                .name(name)
                .contact(contact)
                .suppliedProducts(suppliedProducts)
                .build());
    }

    // ------------------------------------------------------------------ supplies

    private void seedSupplies(Catalog catalog, UserApp manager) {
        supply(catalog.product(0), 20, catalog.supplier(0), manager, calendar.startOfDaysAgo(1).plusHours(7));
        supply(catalog.product(3), 30, catalog.supplier(0), manager, calendar.startOfDaysAgo(2).plusHours(8));
        supply(catalog.product(4), 10, catalog.supplier(1), manager, calendar.startOfDaysAgo(3).plusHours(9));
        supply(catalog.product(6), 12, catalog.supplier(2), manager, calendar.startOfDaysAgo(4).plusHours(7));
        supply(catalog.product(10), 24, catalog.supplier(3), manager, calendar.startOfDaysAgo(5).plusHours(10));
    }

    private void supply(Product product, int quantity, Supplier supplier, UserApp by, LocalDateTime when) {
        movements.save(Supply.builder()
                .quantity(quantity)
                .movementDate(when)
                .product(product)
                .performedBy(by)
                .supplier(supplier)
                .unitCost(product.getAverageCost())
                .build());
    }

    // ------------------------------------------------------------------- trading

    /**
     * A day and a half of trading, arranged so every screen has something to show: paid and
     * unpaid orders, deliveries still pending, cash sitting in an agent's hands, one slip
     * awaiting acknowledgement and one already confirmed.
     */
    private void seedTrading(Catalog catalog, Accounts accounts) {
        LocalDateTime yesterday = calendar.startOfDaysAgo(1);
        LocalDateTime today = calendar.startOfToday();

        // --- yesterday: settled and delivered, cash already remitted and confirmed
        Invoice past1 = record(accounts.fatima(), yesterday.plusHours(15).plusMinutes(30), "Hotely Vaha",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, true,
                line(catalog.product(1), 3));
        Payment remitted = pay(past1, PaymentMethod.CASH, accounts.joseph(), yesterday.plusHours(16).plusMinutes(40));

        Invoice past2 = record(accounts.hary(), yesterday.plusHours(16).plusMinutes(5), "Tiana Rabe",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, true,
                line(catalog.product(0), 1), line(catalog.product(9), 2));
        pay(past2, PaymentMethod.CASH, accounts.hary(), yesterday.plusHours(16).plusMinutes(5));

        CashRemittance confirmed = remittances.save(CashRemittance.builder()
                .amount(remitted.getAmount())
                .remittanceDate(yesterday.plusHours(17))
                .status(RemittanceStatus.CONFIRMED)
                .submittedBy(accounts.joseph())
                .confirmedBy(accounts.fatima())
                .build());
        remitted.setCashRemittance(confirmed);

        // --- today: paid at the desk, already handed over
        Invoice paidDelivered = record(accounts.fatima(), today.plusHours(8).plusMinutes(40), "Rina Hasina",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, true,
                line(catalog.product(0), 2), line(catalog.product(1), 1));
        pay(paidDelivered, PaymentMethod.CASH, accounts.fatima(), today.plusHours(8).plusMinutes(40));

        // --- today: paid by mobile money, still waiting at the depot
        Invoice paidPending = record(accounts.fatima(), today.plusHours(10).plusMinutes(2), "Client comptoir",
                PaymentStatus.PAID, DeliveryStatus.PENDING, false,
                line(catalog.product(5), 4), line(catalog.product(6), 2));
        pay(paidPending, PaymentMethod.MOBILE_MONEY, accounts.fatima(), today.plusHours(10).plusMinutes(2));

        Invoice paidDelivered2 = record(accounts.hary(), today.plusHours(11).plusMinutes(20), "Miora Ranaivo",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, false,
                line(catalog.product(8), 2), line(catalog.product(10), 1));
        pay(paidDelivered2, PaymentMethod.CASH, accounts.hary(), today.plusHours(11).plusMinutes(20));

        // --- today: unpaid, to be settled at the depot on hand-over
        record(accounts.hary(), today.plusHours(9).plusMinutes(15), "Tovo Rakotondrazaka",
                PaymentStatus.UNPAID, DeliveryStatus.PENDING, false,
                line(catalog.product(4), 1), line(catalog.product(7), 3));
        record(accounts.fatima(), today.plusHours(12).plusMinutes(5), "Epicerie Ambohipo",
                PaymentStatus.UNPAID, DeliveryStatus.PENDING, false,
                line(catalog.product(3), 5));

        // --- today: settled at the depot, cash still in Joseph's hands
        Invoice collectedAtDepot = record(accounts.hary(), today.plusHours(9).plusMinutes(50), "Solo Andriamana",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, false,
                line(catalog.product(2), 2));
        pay(collectedAtDepot, PaymentMethod.CASH, accounts.joseph(), today.plusHours(11).plusMinutes(45));

        // --- today: settled at the depot and already handed over, slip pending
        Invoice submittedSlip = record(accounts.fatima(), today.plusHours(8).plusMinutes(10), "Gargote Anosy",
                PaymentStatus.PAID, DeliveryStatus.DELIVERED, false,
                line(catalog.product(11), 2));
        Payment awaiting = pay(submittedSlip, PaymentMethod.CASH, accounts.joseph(), today.plusHours(10).plusMinutes(30));

        CashRemittance pending = remittances.save(CashRemittance.builder()
                .amount(awaiting.getAmount())
                .remittanceDate(today.plusHours(11))
                .status(RemittanceStatus.PENDING)
                .submittedBy(accounts.joseph())
                .confirmedBy(null)
                .build());
        awaiting.setCashRemittance(pending);
    }

    /** Writes a sale, its invoice and the stock outputs it caused, and lowers the stock. */
    private Invoice record(UserApp seller, LocalDateTime when, String client, PaymentStatus paymentStatus,
                           DeliveryStatus deliveryStatus, boolean printed, SeedLine... seedLines) {
        Sale sale = Sale.builder()
                .saleDate(when)
                .paymentStatus(paymentStatus)
                .seller(seller)
                .totalAmount(BigDecimal.ZERO)
                .lines(new ArrayList<>())
                .build();

        for (SeedLine seedLine : seedLines) {
            sale.getLines().add(SaleLine.builder()
                    .sale(sale)
                    .product(seedLine.product())
                    .unitPrice(seedLine.product().getPrice())
                    .unitCost(PurchaseCosting.costOfSale(seedLine.product()))
                    .quantity(seedLine.quantity())
                    .build());
            seedLine.product().adjustStock(-seedLine.quantity());
        }
        sale.calculateTotal();
        Sale saved = sales.saveAndFlush(sale);

        Invoice invoice = invoices.save(Invoice.builder()
                .invoiceNumber(InvoiceNumbering.forSale(saved.getId()))
                .invoiceDate(when)
                .clientName(client)
                .paymentStatus(paymentStatus)
                .deliveryStatus(deliveryStatus)
                .printed(printed)
                .sale(saved)
                .build());

        for (SaleLine line : saved.getLines()) {
            movements.save(StockOutput.builder()
                    .quantity(line.getQuantity())
                    .movementDate(when)
                    .product(line.getProduct())
                    .performedBy(seller)
                    .invoice(invoice)
                    .build());
        }
        return invoice;
    }

    private Payment pay(Invoice invoice, PaymentMethod method, UserApp collectedBy, LocalDateTime when) {
        return payments.save(Payment.builder()
                .amount(invoice.getSale().getTotalAmount())
                .paymentMethod(method)
                .paymentDate(when)
                .invoice(invoice)
                .collectedBy(collectedBy)
                .cashRemittance(null)
                .build());
    }

    private static SeedLine line(Product product, int quantity) {
        return new SeedLine(product, quantity);
    }

    private record SeedLine(Product product, int quantity) {
    }

    private record Accounts(UserApp fatima, UserApp hary, UserApp joseph, UserApp nadia, UserApp mparany) {
    }

    private record Catalog(List<Product> products, List<Supplier> suppliers) {

        Product product(int index) {
            return products.get(index);
        }

        Supplier supplier(int index) {
            return suppliers.get(index);
        }
    }
}
