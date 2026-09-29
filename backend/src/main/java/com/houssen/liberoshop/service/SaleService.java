package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public SaleService(SaleRepository sales, InvoiceRepository invoices, ProductRepository products,
                       PaymentRepository payments, StockMovementRepository movements,
                       BusinessCalendar calendar, ApplicationEventPublisher events) {
        this.sales = sales;
        this.invoices = invoices;
        this.products = products;
        this.payments = payments;
        this.movements = movements;
        this.calendar = calendar;
        this.events = events;
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
        Map<Long, Integer> quantities = mergeLines(request.lines());
        LocalDateTime now = calendar.now();

        Sale sale = Sale.builder()
                .saleDate(now)
                .paymentStatus(request.paymentStatus())
                .seller(seller)
                .lines(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            // Locked for the duration of the transaction: two desks selling the last unit
            // at the same moment must not both read the same stale quantity.
            Product product = products.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> ResourceNotFoundException.of("Produit", entry.getKey()));
            int quantity = entry.getValue();
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(product.getName(), quantity, product.getStockQuantity());
            }

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
                .paymentStatus(request.paymentStatus())
                .deliveryStatus(DeliveryStatus.PENDING)
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

        if (request.paymentStatus() == PaymentStatus.PAID) {
            payments.save(Payment.builder()
                    .amount(savedSale.getTotalAmount())
                    .paymentMethod(methodOf(request))
                    .paymentDate(now)
                    .invoice(invoice)
                    .collectedBy(seller)
                    // Collected at the desk: there is no depot cash to hand over.
                    .cashRemittance(null)
                    .build());
        }

        // Heard after the commit, not now: a sale rolled back must not reach any screen.
        events.publishEvent(new SaleRecordedEvent(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClientName(),
                savedSale.getTotalAmount(),
                request.paymentStatus(),
                savedSale.getLines().size(),
                savedSale.getLines().stream().mapToInt(SaleLine::getQuantity).sum(),
                seller.getId(),
                seller.getFullName()));

        return InvoiceResponse.of(invoice);
    }

    /**
     * Folds duplicate lines so the same product posted twice locks one row and checks its
     * stock once, instead of passing two half-checks that overdraw together.
     */
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

    private static String clientNameOf(CreateSaleRequest request) {
        String name = request.clientName();
        return name == null || name.isBlank() ? "Client comptoir" : name.trim();
    }

    private static PaymentMethod methodOf(CreateSaleRequest request) {
        return request.paymentMethod() == null ? PaymentMethod.CASH : request.paymentMethod();
    }
}
