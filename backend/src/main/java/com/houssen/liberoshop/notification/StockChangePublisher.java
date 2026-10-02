package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.service.CatalogService;
import com.houssen.liberoshop.service.StockChangedEvent;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Tells every screen showing products that their stock moved: two tills selling the same rice see
 * the same count, and the article sold out at one turns red at the other before it is rung up.
 *
 * <p>The counterpart of {@link OrderChangePublisher} for the catalogue, and built the same way: a
 * topic any signed-in screen may follow -- the catalogue is the whole shop's -- carrying the
 * products as they now stand, read back after the commit exactly as {@code GET /api/products}
 * serves them. Should that read fail, the ids still go out and the screens reload.
 */
@Component
public class StockChangePublisher {

    /** {@code /topic/stock} on the socket. */
    public static final String TOPIC = "stock";

    private static final Logger log = LoggerFactory.getLogger(StockChangePublisher.class);

    private final NotificationService notifications;
    private final CatalogService catalog;

    public StockChangePublisher(NotificationService notifications, CatalogService catalog) {
        this.notifications = notifications;
        this.catalog = catalog;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        List<ProductResponse> current = List.of();
        try {
            current = catalog.findAllById(event.productIds());
        } catch (RuntimeException e) {
            // The stock moved whatever happens here: the screens are told without the figures.
            log.warn("Products {} not read back for the stock message: {}", event.productIds(), e.getMessage());
        }
        notifications.publish(TOPIC, new Change(event.productIds(), current));
    }

    /**
     * @param productIds the products whose stock moved
     * @param products   those products as they now stand; empty when they could not be read back,
     *                   in which case a screen reloads instead
     */
    public record Change(List<Long> productIds, List<ProductResponse> products) {
    }
}
