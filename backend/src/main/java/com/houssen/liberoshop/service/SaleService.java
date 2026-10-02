package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.SaleLine;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.InvoiceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.InsufficientStockException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Checkout at the cash desk.
 *
 * <p>One transaction writes the whole aggregate: the sale and its lines, the invoice, one
 * stock output per line, and the payment when the customer settles immediately. Either all
 * of it lands or none does -- a sale that decremented stock without producing an invoice
 * would be a stock discrepancy nobody could explain afterwards.
 *
 * <p>What happens because of a sale, beyond that aggregate, is not written here: the checkout
 * publishes a {@link SaleRecordedEvent} and whoever cares listens for it.
 */
@Service
public class SaleService {

    private final SaleRepository sales;
    private final InvoiceRepository invoices;
    private final ProductRepository products;
    private final PaymentRepository payments;
    private final StockMovementRepository movements;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;
    private final ShopSettingsService settings;

    public SaleService(SaleRepository sales, InvoiceRepository invoices, ProductRepository products,
                       PaymentRepository payments, StockMovementRepository movements,
                       BusinessCalendar calendar, ApplicationEventPublisher events,
                       ShopSettingsService settings) {
        this.sales = sales;
        this.invoices = invoices;
        this.products = products;
        this.payments = payments;
        this.movements = movements;
        this.calendar = calendar;
        this.events = events;
        this.settings = settings;
    }

    /**
     * Records a sale and returns the invoice to print.
     *
     * <p>Guarded by the license module: once the license has expired past its grace
     * period, this call is refused and the installation is read-only.
     */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse checkout(CreateSaleRequest request, UserApp seller) {
        if (!request.paymentStatus().isCheckoutStatus()) {
            throw new BusinessRuleException("INVALID_PAYMENT_STATUS",
                    "Une vente est payee ou non payee a la caisse : les autres statuts viennent du depot.");
        }
        PaymentStatus status = settledStatusOf(request, seller);
        Map<Long, Integer> quantities = mergeLines(request.lines());
        LocalDateTime now = calendar.now();

        Sale sale = Sale.builder()
                .saleDate(now)
                .paymentStatus(status)
                .seller(seller)
                .lines(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        Map<Product, Integer> wanted = lockAndCheckStock(quantities);
        for (Map.Entry<Product, Integer> entry : wanted.entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();

            sale.getLines().add(SaleLine.builder()
                    .sale(sale)
                    .product(product)
                    // Both frozen here: a later price change must not rewrite past receipts,
                    // and a later delivery at another cost must not rewrite past margins.
                    .unitPrice(product.getPrice())
                    .unitCost(PurchaseCosting.costOfSale(product))
                    .quantity(quantity)
                    .build());

            product.adjustStock(-quantity);
        }

        sale.calculateTotal();
        // Flushed now so the generated identifier can number the invoice.
        Sale savedSale = sales.saveAndFlush(sale);

        Invoice invoice = invoices.save(Invoice.builder()
                .invoiceNumber(InvoiceNumbering.forSale(savedSale.getId()))
                .invoiceDate(now)
                .clientName(clientNameOf(request))
                .paymentStatus(status)
                // With no depot or kitchen in between, the customer leaves with the goods.
                .deliveryStatus(settings.features().separateDelivery()
                        ? DeliveryStatus.PENDING : DeliveryStatus.DELIVERED)
                .printed(false)
                .sale(savedSale)
                .build());

        for (SaleLine line : savedSale.getLines()) {
            movements.save(StockOutput.builder()
                    .quantity(line.getQuantity())
                    .movementDate(now)
                    .product(line.getProduct())
                    .performedBy(seller)
                    .invoice(invoice)
                    .build());
        }

        if (status != PaymentStatus.UNPAID) {
            payments.save(Payment.builder()
                    .amount(savedSale.getTotalAmount())
                    // Cash in hand is counted at the till later: it can only be cash.
                    .paymentMethod(status == PaymentStatus.COLLECTED ? PaymentMethod.CASH : methodOf(request))
                    .paymentDate(now)
                    .invoice(invoice)
                    .collectedBy(seller)
                    // PAID at the desk: already in the till. COLLECTED: the order taker's cash in
                    // hand, until they bring it to the till (RemittanceService).
                    .cashRemittance(null)
                    .build());
        }

        // Heard after the commit, not now: a sale rolled back must not reach any screen.
        events.publishEvent(new SaleRecordedEvent(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClientName(),
                savedSale.getTotalAmount(),
                status,
                savedSale.getLines().size(),
                savedSale.getLines().stream().mapToInt(SaleLine::getQuantity).sum(),
                seller.getId(),
                seller.getFullName()));
        events.publishEvent(new StockChangedEvent(wanted.keySet().stream().map(Product::getId).toList()));

        return InvoiceResponse.of(invoice);
    }

    /**
     * Folds duplicate lines so the same product posted twice locks one row and checks its
     * stock once, instead of passing two half-checks that overdraw together.
     */
    /**
     * Every product of the sale, locked, once the shelf is known to hold enough of each.
     *
     * <p>Checked in full before anything is written, so a refusal names every short line --
     * the till marks them all in red at once -- and leaves no half-built sale behind.
     *
     * <p>Locked for the rest of the transaction: two desks selling the last unit at the same
     * moment must not both read the same stale quantity. Locked in id order, so two sales
     * sharing products always take their locks in the same order and cannot deadlock.
     */
    private Map<Product, Integer> lockAndCheckStock(Map<Long, Integer> quantities) {
        Map<Long, Product> locked = new HashMap<>();
        for (Long productId : new TreeSet<>(quantities.keySet())) {
            locked.put(productId, products.findByIdForUpdate(productId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId)));
        }

        // From here on in the order the cashier entered the lines: the order of the receipt,
        // and of the refusal's list.
        Map<Product, Integer> wanted = new LinkedHashMap<>();
        List<InsufficientStockException.Shortage> shortages = new ArrayList<>();
        quantities.forEach((productId, quantity) -> {
            Product product = locked.get(productId);
            if (product.getStockQuantity() < quantity) {
                shortages.add(new InsufficientStockException.Shortage(
                        productId, product.getName(), quantity, product.getStockQuantity()));
            }
            wanted.put(product, quantity);
        });
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }
        return wanted;
    }

    private static Map<Long, Integer> mergeLines(List<CreateSaleRequest.Line> lines) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CreateSaleRequest.Line line : lines) {
            merged.merge(line.productId(), line.quantity(), Integer::sum);
        }
        if (merged.isEmpty()) {
            throw new BusinessRuleException("EMPTY_CART", "Le panier est vide.");
        }
        return merged;
    }

    /**
     * What a sale "paid" by its seller really is. At a till it is paid. Taken by someone who only
     * takes orders, it is their cash in hand -- {@link PaymentStatus#COLLECTED}, on its way to the
     * till like the depot's -- and only when the shop lets order takers take money at all.
     */
    private PaymentStatus settledStatusOf(CreateSaleRequest request, UserApp seller) {
        if (request.paymentStatus() == PaymentStatus.UNPAID || seller.hasRole(RoleApp.CASHIER)) {
            return request.paymentStatus();
        }
        if (!settings.features().orderTakerCollects()) {
            throw new BusinessRuleException("ORDER_TAKER_CANNOT_COLLECT",
                    "Une prise de commande ne peut pas encaisser : la commande est enregistree non payee.");
        }
        return PaymentStatus.COLLECTED;
    }

    private static String clientNameOf(CreateSaleRequest request) {
        String name = request.clientName();
        return name == null || name.isBlank() ? "Client comptoir" : name.trim();
    }

    private static PaymentMethod methodOf(CreateSaleRequest request) {
        return request.paymentMethod() == null ? PaymentMethod.CASH : request.paymentMethod();
    }
}
