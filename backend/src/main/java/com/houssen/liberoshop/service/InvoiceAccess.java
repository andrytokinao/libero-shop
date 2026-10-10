package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which orders an account may read.
 *
 * <p>Three widths, decided by the widest role the account holds:
 * <ul>
 *   <li>{@link Scope#EVERYTHING} -- whoever answers for the shop or its stock reads the whole
 *       history;</li>
 *   <li>{@link Scope#CURRENT} -- the till and the depot work on every order of the day, whoever
 *       took it: the cashier settles a colleague's unpaid order, the storekeeper serves it. So
 *       they read today's orders and the ones still open, plus their own of any date;</li>
 *   <li>{@link Scope#OWN} -- an order taker reads the orders they took, and nothing else.</li>
 * </ul>
 *
 * <p>What this closes is the history: before, any signed-in account could list every sale the
 * shop ever made, and add them up.
 */
@Component
public class InvoiceAccess {

    /** How much of the shop's orders an account reads. */
    public enum Scope {
        EVERYTHING, CURRENT, OWN
    }

    private final PaymentRepository payments;
    private final BusinessCalendar calendar;

    public InvoiceAccess(PaymentRepository payments, BusinessCalendar calendar) {
        this.payments = payments;
        this.calendar = calendar;
    }

    /** The widest scope among the roles: an owner who cumulates them is not narrowed by one. */
    public static Scope scopeOf(Collection<RoleApp> roles) {
        if (roles.contains(RoleApp.SUPER_ADMIN) || roles.contains(RoleApp.DEPOT_MANAGER)) {
            return Scope.EVERYTHING;
        }
        if (roles.contains(RoleApp.CASHIER) || roles.contains(RoleApp.DEPOT_AGENT)) {
            return Scope.CURRENT;
        }
        return Scope.OWN;
    }

    /**
     * The test an order must pass to be shown to {@code reader}. Built once per request: the
     * orders whose money passed through the reader's hands are read at the first order that
     * needs them, and once.
     */
    public Predicate<Invoice> visibleTo(UserApp reader) {
        Scope scope = scopeOf(reader.getRoles());
        if (scope == Scope.EVERYTHING) {
            return invoice -> true;
        }
        Long readerId = reader.getId();
        Set<Long>[] collected = new Set[1];
        return invoice -> {
            if (scope == Scope.CURRENT && (isOpen(invoice) || calendar.isToday(invoice.getInvoiceDate()))) {
                return true;
            }
            if (isBy(invoice.getSale().getSeller(), readerId) || isBy(invoice.getHandledBy(), readerId)
                    || isBy(invoice.getCancelledBy(), readerId)) {
                return true;
            }
            if (collected[0] == null) {
                collected[0] = payments.findInvoiceIdsWhoseCashPassedBy(readerId);
            }
            return collected[0].contains(invoice.getId());
        };
    }

    /** Still waiting for someone: its money is not in the till, or its goods are not handed over. */
    private static boolean isOpen(Invoice invoice) {
        PaymentStatus payment = invoice.getPaymentStatus();
        DeliveryStatus delivery = invoice.getDeliveryStatus();
        return payment == PaymentStatus.UNPAID || payment == PaymentStatus.COLLECTED
                || payment == PaymentStatus.REMITTED
                || delivery == DeliveryStatus.PENDING || delivery == DeliveryStatus.IN_PROGRESS;
    }

    private static boolean isBy(UserApp user, Long readerId) {
        return user != null && readerId.equals(user.getId());
    }
}
