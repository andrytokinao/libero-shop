package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.CashRemittance;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CashRemittanceRepository;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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

    public RemittanceService(CashRemittanceRepository remittances, PaymentRepository payments,
                             BusinessCalendar calendar) {
        this.remittances = remittances;
        this.payments = payments;
        this.calendar = calendar;
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
     * Bundles everything the agent currently holds into one slip, marked PENDING until the
     * desk confirms the money physically arrived.
     */
    @RequiresActiveLicense
    @Transactional
    public CashRemittanceResponse submit(UserApp agent) {
        List<Payment> held = payments.findUnremittedByCollector(agent.getId());
        if (held.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_REMIT",
                    "Vous n'avez aucune espece en main a verser.");
        }

        CashRemittance remittance = remittances.save(CashRemittance.builder()
                .amount(sum(held))
                .remittanceDate(calendar.now())
                .status(RemittanceStatus.PENDING)
                .submittedBy(agent)
                .confirmedBy(null)
                .build());

        // Attaching the payments is what makes the slip auditable down to the invoice.
        held.forEach(payment -> payment.setCashRemittance(remittance));

        return CashRemittanceResponse.of(remittance, held.size());
    }

    /** The cash desk acknowledges receipt. */
    @RequiresActiveLicense
    @Transactional
    public CashRemittanceResponse confirm(Long remittanceId, UserApp cashier) {
        CashRemittance remittance = remittances.findById(remittanceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Versement", remittanceId));

        if (remittance.getStatus() == RemittanceStatus.CONFIRMED) {
            throw new BusinessRuleException("ALREADY_CONFIRMED",
                    "Le versement V-" + remittanceId + " a deja ete confirme.");
        }
        if (remittance.getSubmittedBy().getId().equals(cashier.getId())) {
            throw new BusinessRuleException("SELF_CONFIRMATION",
                    "Un versement ne peut pas etre confirme par la personne qui l'a depose.");
        }

        remittance.setStatus(RemittanceStatus.CONFIRMED);
        remittance.setConfirmedBy(cashier);
        return toResponse(remittance);
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
        return CashRemittanceResponse.of(remittance,
                payments.findByCashRemittanceId(remittance.getId()).size());
    }

    private static BigDecimal sum(List<Payment> list) {
        return list.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
