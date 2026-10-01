package com.houssen.liberoshop.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rules an order's statuses obey, whatever the shop's settings: the money and the goods only
 * move along their transitions, and the sale follows its invoice.
 */
class InvoiceLifecycleTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 1, 12, 0);

    private static Invoice order(PaymentStatus payment, DeliveryStatus delivery) {
        Sale sale = Sale.builder().saleDate(NOON).paymentStatus(payment).totalAmount(new BigDecimal("8000")).build();
        return Invoice.builder().invoiceNumber("F-1031").invoiceDate(NOON).clientName("Rina")
                .paymentStatus(payment).deliveryStatus(delivery).sale(sale).build();
    }

    static Stream<Arguments> everyPaymentTransitionFromEveryStatus() {
        return Arrays.stream(PaymentTransition.values())
                .flatMap(transition -> Arrays.stream(PaymentStatus.values()).map(status -> Arguments.of(transition, status)));
    }

    @ParameterizedTest(name = "{0} depuis {1}")
    @MethodSource("everyPaymentTransitionFromEveryStatus")
    @DisplayName("each payment transition moves from its own status, and is refused from any other")
    void paymentTable(PaymentTransition transition, PaymentStatus current) {
        Invoice invoice = order(current, DeliveryStatus.PENDING);
        if (current == transition.from()) {
            invoice.apply(transition);
            assertEquals(transition.to(), invoice.getPaymentStatus());
            assertEquals(transition.to(), invoice.getSale().getPaymentStatus(), "the sale follows its invoice");
        } else {
            assertThrows(StatusTransitionException.class, () -> invoice.apply(transition));
            assertEquals(current, invoice.getPaymentStatus(), "a refused transition changes nothing");
            assertEquals(current, invoice.getSale().getPaymentStatus());
        }
    }

    static Stream<Arguments> everyDeliveryTransitionFromEveryStatus() {
        return Arrays.stream(DeliveryTransition.values())
                .flatMap(transition -> Arrays.stream(DeliveryStatus.values()).map(status -> Arguments.of(transition, status)));
    }

    @ParameterizedTest(name = "{0} depuis {1}")
    @MethodSource("everyDeliveryTransitionFromEveryStatus")
    @DisplayName("each delivery transition moves from its own status, and is refused from any other")
    void deliveryTable(DeliveryTransition transition, DeliveryStatus current) {
        Invoice invoice = order(PaymentStatus.UNPAID, current);
        if (current == transition.from()) {
            invoice.apply(transition);
            assertEquals(transition.to(), invoice.getDeliveryStatus());
        } else {
            assertThrows(StatusTransitionException.class, () -> invoice.apply(transition));
            assertEquals(current, invoice.getDeliveryStatus());
        }
    }

    @Test
    @DisplayName("cash on its way to the till cannot be paid at the till a second time")
    void remittedIsNotPaidAtTill() {
        Invoice invoice = order(PaymentStatus.UNPAID, DeliveryStatus.PENDING);
        invoice.apply(PaymentTransition.COLLECT);
        invoice.apply(PaymentTransition.REMIT);

        StatusTransitionException refused =
                assertThrows(StatusTransitionException.class, () -> invoice.apply(PaymentTransition.PAY_AT_TILL));
        assertEquals("ALREADY_SETTLED", refused.code());

        invoice.apply(PaymentTransition.CONFIRM_REMITTANCE);
        assertEquals(PaymentStatus.PAID, invoice.getPaymentStatus());
    }

    @Test
    @DisplayName("cancelled before hand-over: money and goods both cancelled, and who, when and why kept")
    void cancelPending() {
        Invoice invoice = order(PaymentStatus.UNPAID, DeliveryStatus.PENDING);
        UserApp cashier = UserApp.builder().fullName("Fatima").build();

        invoice.cancel(cashier, CancelReason.INPUT_ERROR, null, NOON);

        assertEquals(PaymentStatus.CANCELLED, invoice.getPaymentStatus());
        assertEquals(PaymentStatus.CANCELLED, invoice.getSale().getPaymentStatus());
        assertEquals(DeliveryStatus.CANCELLED, invoice.getDeliveryStatus());
        assertEquals(cashier, invoice.getCancelledBy());
        assertEquals(CancelReason.INPUT_ERROR, invoice.getCancelReason());
        assertEquals(NOON, invoice.getCancelledAt());
    }

    @Test
    @DisplayName("cancelled after hand-over: only the money is cancelled, the goods did leave")
    void cancelDelivered() {
        Invoice invoice = order(PaymentStatus.UNPAID, DeliveryStatus.DELIVERED);

        invoice.cancel(UserApp.builder().fullName("Fatima").build(), CancelReason.CUSTOMER_GAVE_UP, "Parti", NOON);

        assertEquals(PaymentStatus.CANCELLED, invoice.getPaymentStatus());
        assertEquals(DeliveryStatus.DELIVERED, invoice.getDeliveryStatus());
    }

    @Test
    @DisplayName("a paid order is never cancelled, and refusing it records nothing")
    void paidIsNotCancelled() {
        Invoice invoice = order(PaymentStatus.PAID, DeliveryStatus.PENDING);

        StatusTransitionException refused = assertThrows(StatusTransitionException.class,
                () -> invoice.cancel(UserApp.builder().build(), CancelReason.INPUT_ERROR, null, NOON));

        assertEquals("ALREADY_SETTLED", refused.code());
        assertEquals(DeliveryStatus.PENDING, invoice.getDeliveryStatus(), "the goods were not cancelled either");
        assertNull(invoice.getCancelledAt());
    }

    @Test
    @DisplayName("refusals keep the codes the screens already know")
    void refusalCodes() {
        assertEquals("ALREADY_DELIVERED", assertThrows(StatusTransitionException.class,
                () -> order(PaymentStatus.UNPAID, DeliveryStatus.DELIVERED).apply(DeliveryTransition.HAND_OVER)).code());
        assertEquals("CANCELLED", assertThrows(StatusTransitionException.class,
                () -> order(PaymentStatus.CANCELLED, DeliveryStatus.CANCELLED).apply(DeliveryTransition.HAND_OVER)).code());
        assertEquals("ALREADY_CANCELLED", assertThrows(StatusTransitionException.class,
                () -> order(PaymentStatus.CANCELLED, DeliveryStatus.PENDING).apply(PaymentTransition.PAY_AT_TILL)).code());
    }
}
