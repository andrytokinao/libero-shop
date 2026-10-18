package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.CancelReason;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.StockOutputPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** An order cancelled before hand-over gives its goods back by an opposite movement, never by deleting. */
@SpringBootTest
class OrderCancellationStockTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private StockService stockService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private StockMovementRepository movements;
    @Autowired
    private UserAppRepository users;

    private UserApp cashier() {
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Caisse " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
    }

    @Test
    @DisplayName("commande annulee : la sortie reste, marquee, et une correction inverse remet le stock")
    void sortieContrePasseeEtNonSupprimee() {
        String name = "Huile " + UUID.randomUUID().toString().substring(0, 8);
        Product huile = products.save(Product.builder().name(name).price(new BigDecimal("6800"))
                .stockQuantity(qty(10)).build());
        UserApp fatima = cashier();
        InvoiceResponse order = saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(huile.getId(), qty(3)))), fatima);

        invoiceService.cancel(order.id(), CancelReason.INPUT_ERROR, null, fatima);

        assertQuantity(10, products.findById(huile.getId()).orElseThrow().getStockQuantity());
        List<StockOutput> outputs = movements.findOutputsOfInvoice(order.id());
        assertEquals(1, outputs.size(), "the sale stays in the history");
        assertNotNull(outputs.getFirst().getCancelledAt());
        StockAdjustment opposite = movements.findLatestAdjustments(org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(adjustment -> adjustment.getProduct().getId().equals(huile.getId()))
                .findFirst().orElseThrow();
        assertEquals(AdjustmentReason.CANCELLATION, opposite.getReason());
        assertQuantity(3, opposite.getQuantity());
        assertEquals("Annulation de la commande " + order.invoiceNumber(), opposite.getComment());
    }

    @Test
    @DisplayName("les sorties d'une commande annulee sont listees mais ne comptent plus dans les totaux")
    void sortiesAnnuleesHorsDesTotaux() {
        String name = "Sel " + UUID.randomUUID().toString().substring(0, 8);
        Product sel = products.save(Product.builder().name(name).price(new BigDecimal("500"))
                .stockQuantity(qty(20)).build());
        UserApp fatima = cashier();
        InvoiceResponse kept = saleService.checkout(new CreateSaleRequest("A", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(sel.getId(), qty(2)))), fatima);
        InvoiceResponse cancelled = saleService.checkout(new CreateSaleRequest("B", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(sel.getId(), qty(5)))), fatima);
        invoiceService.cancel(cancelled.id(), CancelReason.INPUT_ERROR, null, fatima);

        StockOutputPageResponse page = stockService.findOutputs(name, 0, 10);

        assertEquals(2, page.items().size());
        assertQuantity(2, page.totalQuantity());
        assertEquals(0, new BigDecimal("1000").compareTo(page.totalValue()));
        assertEquals(1, page.items().stream().filter(item -> item.cancelledAt() != null).count());
        assertNotNull(kept);
    }
}
