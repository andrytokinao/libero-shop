package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A checkout as posted by the cash desk.
 *
 * <p>Note what is absent: the seller and the unit prices. The seller is the authenticated
 * user, and the prices are re-read from the catalogue inside the transaction, so a
 * tampered payload cannot sell at a price of its choosing or under another name.
 *
 * @param paymentMethod   ignored when {@code paymentStatus} is UNPAID: nothing is collected yet
 * @param clientReference a name the till gives this basket, the same on every attempt to post
 *                        it: a sale already recorded under it is returned instead of being sold
 *                        a second time. Null from a client that does not send one -- every post
 *                        is then a sale, as before.
 */
public record CreateSaleRequest(@Size(max = 120) String clientName,
                                @NotNull PaymentStatus paymentStatus,
                                PaymentMethod paymentMethod,
                                @NotEmpty @Valid List<Line> lines,
                                @Size(max = MAX_REFERENCE_LENGTH) String clientReference) {

    public static final int MAX_REFERENCE_LENGTH = 64;

    /** A sale without a reference: what every caller posted before there was one. */
    public CreateSaleRequest(String clientName, PaymentStatus paymentStatus, PaymentMethod paymentMethod,
                             List<Line> lines) {
        this(clientName, paymentStatus, paymentMethod, lines, null);
    }

    /**
     * @param packagingId the unit sold -- "kg", "sac" -- or null for the product's base unit
     * @param quantity    in that unit, to the thousandth: 0.5 for half a kilo
     */
    public record Line(@NotNull Long productId, Long packagingId,
                       @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity) {

        /** A line in the base unit, the only kind there was before units. */
        public Line(Long productId, BigDecimal quantity) {
            this(productId, null, quantity);
        }
    }
}
