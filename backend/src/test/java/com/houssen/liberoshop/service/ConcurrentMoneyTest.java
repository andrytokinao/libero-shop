package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.StatusTransitionException;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same money asked for twice at the same instant: two tills settling one order, one agent's
 * cash filed on two slips, one sale posted twice by a till whose network dropped.
 *
 * <p>Each scenario is raced many times over, both calls released together: a race that the code
 * lets through shows up on some round, as a second payment, a second slip or a second sale. The
 * loser of a race must always be refused cleanly -- a conflict the screen can show -- never
 * leave a second trace of the money.
 */
@SpringBootTest
class ConcurrentMoneyTest {

    private static final int ROUNDS = 12;

    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private RemittanceService remittanceService;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private ProductRepository products;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private CashRemittanceRepository remittances;

    @Test
    @DisplayName("deux caisses encaissent la meme commande : un seul paiement")
    void twoTillsSettlingTheSameOrder() throws Exception {
        UserApp first = account(RoleApp.CASHIER);
        UserApp second = account(RoleApp.CASHIER);

        for (int round = 0; round < ROUNDS; round++) {
            InvoiceResponse order = unpaidSale(first, "8000");

            List<Outcome> outcomes = race(
                    () -> invoiceService.pay(order.id(), PaymentMethod.CASH, first),
                    () -> invoiceService.pay(order.id(), PaymentMethod.CASH, second));

            assertEquals(1, paymentsOf(order), "round " + round + ": the order was paid twice");
            assertEquals(1, successes(outcomes), "round " + round);
            assertRefusedCleanly(outcomes);
            assertEquals(PaymentStatus.PAID, invoiceService.findById(order.id()).paymentStatus());
        }
    }

    @Test
    @DisplayName("le magasinier et la caisse encaissent la meme commande : un seul paiement")
    void depotAndTillTakingTheSameMoney() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);

        for (int round = 0; round < ROUNDS; round++) {
            InvoiceResponse order = unpaidSale(cashier, "5000");

            List<Outcome> outcomes = race(
                    () -> invoiceService.pay(order.id(), PaymentMethod.CASH, cashier),
                    () -> invoiceService.deliver(order.id(), storekeeper));

            assertEquals(1, paymentsOf(order), "round " + round + ": the customer's money was written twice");
            assertRefusedCleanly(outcomes);
        }
    }

    @Test
    @DisplayName("le meme argent verse deux fois en meme temps : un seul bordereau")
    void theSameCashFiledTwice() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);

        for (int round = 0; round < ROUNDS; round++) {
            UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
            InvoiceResponse order = unpaidSale(cashier, "12000");
            invoiceService.deliver(order.id(), storekeeper);

            List<Outcome> outcomes = race(
                    () -> remittanceService.submit(storekeeper, List.of()),
                    () -> remittanceService.submit(storekeeper, List.of(order.id())));

            assertEquals(1, remittances.countBySubmittedById(storekeeper.getId()),
                    "round " + round + ": one sum of money, two slips");
            assertEquals(1, successes(outcomes), "round " + round);
            assertRefusedCleanly(outcomes);
        }
    }

    @Test
    @DisplayName("une vente renvoyee apres une coupure reseau n'est enregistree qu'une fois")
    void aSalePostedAgainIsNotSoldAgain() {
        UserApp cashier = account(RoleApp.CASHIER);
        Product product = product("3000", 10);
        CreateSaleRequest sale = new CreateSaleRequest("Client", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(2))), UUID.randomUUID().toString());

        InvoiceResponse recorded = saleService.checkout(sale, cashier);
        // The answer never reached the till: the cashier touches "Encaisser" again.
        InvoiceResponse again = saleService.checkout(sale, cashier);

        assertEquals(recorded.id(), again.id(), "the same sale, not a second one");
        assertEquals(recorded.invoiceNumber(), again.invoiceNumber());
        assertQuantity(8, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, paymentsOf(recorded));

        // Another basket is another sale, even with the same goods.
        InvoiceResponse next = saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.PAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(product.getId(), qty(2))),
                UUID.randomUUID().toString()), cashier);
        assertTrue(!next.id().equals(recorded.id()));
        assertQuantity(6, products.findById(product.getId()).orElseThrow().getStockQuantity());

        // And a till that sends no reference is served as before: every post is a sale.
        CreateSaleRequest bare = new CreateSaleRequest("Client", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1))));
        assertTrue(!saleService.checkout(bare, cashier).id().equals(saleService.checkout(bare, cashier).id()));
    }

    @Test
    @DisplayName("la meme vente envoyee deux fois au meme instant : une seule vente")
    void theSameSalePostedTwiceAtOnce() throws Exception {
        UserApp cashier = account(RoleApp.CASHIER);

        for (int round = 0; round < ROUNDS; round++) {
            Product product = product("3000", 10);
            CreateSaleRequest sale = new CreateSaleRequest("Client", PaymentStatus.PAID, PaymentMethod.CASH,
                    List.of(new CreateSaleRequest.Line(product.getId(), qty(2))), UUID.randomUUID().toString());

            List<Outcome> outcomes = race(
                    () -> saleService.checkout(sale, cashier),
                    () -> saleService.checkout(sale, cashier));

            assertEquals(2, successes(outcomes), "round " + round + ": both posts get the sale back");
            assertEquals(((InvoiceResponse) outcomes.get(0).result()).id(),
                    ((InvoiceResponse) outcomes.get(1).result()).id(), "round " + round);
            assertQuantity(8, products.findById(product.getId()).orElseThrow().getStockQuantity());
        }
    }

    // -------------------------------------------------------------------------- helpers

    /** What one side of a race came back with. */
    private record Outcome(Object result, Exception failure) {
    }

    /** Runs both calls released at the same instant, and waits for both. */
    private static List<Outcome> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> running = new ArrayList<>();
            for (Callable<Object> call : List.of(first, second)) {
                running.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return new Outcome(call.call(), null);
                    } catch (Exception failure) {
                        return new Outcome(null, failure);
                    }
                }));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : running) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long successes(List<Outcome> outcomes) {
        return outcomes.stream().filter(outcome -> outcome.failure() == null).count();
    }

    /**
     * A loser is told so in a way the screen can show: the shop's rule ("deja payee") when it
     * arrived second, a conflict when it arrived at the same instant. Anything else is a bug.
     */
    private static void assertRefusedCleanly(List<Outcome> outcomes) {
        for (Outcome outcome : outcomes) {
            Exception failure = outcome.failure();
            assertTrue(failure == null
                            || failure instanceof StatusTransitionException
                            || failure instanceof BusinessRuleException
                            || failure instanceof OptimisticLockingFailureException,
                    "unexpected failure: " + failure);
        }
    }

    private long paymentsOf(InvoiceResponse order) {
        return payments.findAll().stream()
                .filter(payment -> payment.getInvoice().getId().equals(order.id()))
                .count();
    }

    private UserApp account(RoleApp... roles) {
        String handle = "u" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    private Product product(String price, int stock) {
        return products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal(price)).stockQuantity(qty(stock)).build());
    }

    private InvoiceResponse unpaidSale(UserApp cashier, String price) {
        return saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product(price, 10).getId(), qty(1)))), cashier);
    }
}
