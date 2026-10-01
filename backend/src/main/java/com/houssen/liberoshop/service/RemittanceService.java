package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.CashRemittance;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.PaymentTransition;
import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.PaymentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The depot cash trail: what an agent holds, what they hand over, what the desk confirms.
 *
 * <p>Both ends take the actor from the security context, never from the request. An agent
 * can only remit their own collections, and only a cash desk can acknowledge a slip --
 * otherwise the control this whole flow exists for would be worth nothing.
 */
@Service
@Transactional(readOnly = true)
public class RemittanceService {

    private final CashRemittanceRepository remittances;
    private final PaymentRepository payments;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;
    private final ShopSettingsService settings;

    public RemittanceService(CashRemittanceRepository remittances, PaymentRepository payments,
                             BusinessCalendar calendar, ApplicationEventPublisher events,
                             ShopSettingsService settings) {
        this.remittances = remittances;
        this.payments = payments;
        this.calendar = calendar;
        this.events = events;
        this.settings = settings;
    }

    /** Collections the given agent has not handed over yet. */
    public List<PaymentResponse> cashInHand(UserApp agent) {
        return payments.findUnremittedByCollector(agent.getId()).stream()
                .map(PaymentResponse::of)
                .toList();
    }

    public BigDecimal cashInHandTotal(UserApp agent) {
        return sum(payments.findUnremittedByCollector(agent.getId()));
    }

    /** Same thing across all agents, for the super-admin control panel. */
    public BigDecimal depotCashInHandTotal() {
        return payments.sumUnremittedDepotCash();
    }

    public long depotCashInHandCount() {
        return payments.findUnremittedDepotCash().size();
    }

    public List<CashRemittanceResponse> search(RemittanceStatus status, Long submittedById) {
        return remittances.search(status, submittedById).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Puts cash the agent holds into one slip, marked PENDING until the desk confirms the money
     * physically arrived. Its orders become {@link PaymentStatus#REMITTED}.
     *
     * @param invoiceIds the orders whose cash is brought -- one, from the button on that order --
     *                   or empty for everything the agent holds
     */
    @RequiresActiveLicense
    @Transactional
    public CashRemittanceResponse submit(UserApp agent, Collection<Long> invoiceIds) {
        List<Payment> held = payments.findUnremittedByCollector(agent.getId());
        if (invoiceIds != null && !invoiceIds.isEmpty()) {
            Set<Long> wanted = Set.copyOf(invoiceIds);
            held = held.stream().filter(payment -> wanted.contains(payment.getInvoice().getId())).toList();
        }
        if (held.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_REMIT", invoiceIds == null || invoiceIds.isEmpty()
                    ? "Vous n'avez aucune espece en main a verser."
                    : "Aucune espece a verser pour cette commande : deja versee, ou encaissee par un autre agent.");
        }

        CashRemittance remittance = remittances.save(CashRemittance.builder()
                .amount(sum(held))
                .remittanceDate(calendar.now())
                .status(RemittanceStatus.PENDING)
                .submittedBy(agent)
                .confirmedBy(null)
                .build());

        // Attaching the payments is what makes the slip auditable down to the invoice.
        held.forEach(payment -> {
            payment.setCashRemittance(remittance);
            payment.getInvoice().apply(PaymentTransition.REMIT);
        });

        events.publishEvent(eventOf(remittance, held));
        return CashRemittanceResponse.of(remittance, held);
    }

    /**
     * The cash desk acknowledges receipt: only now are the orders {@link PaymentStatus#PAID}, and
     * only now does their money count as revenue.
     */
    @RequiresActiveLicense
    @Transactional
    public CashRemittanceResponse confirm(Long remittanceId, UserApp cashier) {
        CashRemittance remittance = remittances.findById(remittanceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Versement", remittanceId));

        if (remittance.getStatus() == RemittanceStatus.CONFIRMED) {
            throw new BusinessRuleException("ALREADY_CONFIRMED",
                    "Le versement V-" + remittanceId + " a deja ete confirme.");
        }
        // A shop run by one person turns the double check off: they would otherwise never get paid.
        if (settings.features().dualControlRemittance()
                && remittance.getSubmittedBy().getId().equals(cashier.getId())) {
            throw new BusinessRuleException("SELF_CONFIRMATION",
                    "Un versement ne peut pas etre confirme par la personne qui l'a depose.");
        }

        remittance.setStatus(RemittanceStatus.CONFIRMED);
        remittance.setConfirmedBy(cashier);
        List<Payment> carried = payments.findByCashRemittanceId(remittance.getId());
        carried.forEach(payment -> payment.getInvoice().apply(PaymentTransition.CONFIRM_REMITTANCE));

        events.publishEvent(eventOf(remittance, carried));
        return CashRemittanceResponse.of(remittance, carried);
    }

    public BigDecimal pendingTotal() {
        return remittances.sumByStatus(RemittanceStatus.PENDING);
    }

    public long pendingCount() {
        return remittances.search(RemittanceStatus.PENDING, null).size();
    }

    public BigDecimal confirmedTodayTotal() {
        return remittances.sumByStatusAndPeriod(RemittanceStatus.CONFIRMED,
                calendar.startOfToday(), calendar.startOfTomorrow());
    }

    private CashRemittanceResponse toResponse(CashRemittance remittance) {
        return CashRemittanceResponse.of(remittance, payments.findByCashRemittanceId(remittance.getId()));
    }

    private static RemittanceRecordedEvent eventOf(CashRemittance remittance, List<Payment> carried) {
        UserApp cashier = remittance.getConfirmedBy();
        return new RemittanceRecordedEvent(remittance.getId(), remittance.getAmount(),
                carried.stream().map(payment -> payment.getInvoice().getInvoiceNumber()).toList(),
                remittance.getSubmittedBy().getId(), remittance.getSubmittedBy().getFullName(),
                remittance.getStatus() == RemittanceStatus.CONFIRMED,
                cashier == null ? null : cashier.getId(), cashier == null ? null : cashier.getFullName());
    }

    private static BigDecimal sum(List<Payment> list) {
        return list.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
