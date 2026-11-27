package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.SupplyCharge;
import com.houssen.liberoshop.entity.SupplyUseCharge;
import com.houssen.liberoshop.repository.SupplyChargeRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The one place that keeps the charge shares and the stored costs of the receipts in step.
 * Receipts passed here must already be saved (managed): dirty checking writes their costs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SupplyCostService {

    private final SupplyChargeRepository chargeRepository;

    /** At entry: splits the charges, saves them, then stores the cost of every receipt. */
    public EntryCostSummary applyCharges(Collection<Supply> receipts,
                                         Collection<SupplyCharge> charges) {
        Set<Supply> affected = new LinkedHashSet<>(receipts);
        for (SupplyCharge charge : charges) {
            charge.allocate();
            chargeRepository.save(charge);
            charge.getSupplys().forEach(use -> affected.add(use.getSupply()));
        }
        affected.forEach(Supply::recalculateCosts);
        return summarize(receipts);
    }

    /**
     * After the quantity, unit cost or factor of a receipt was corrected: its charges are split
     * again, which also changes the other receipts sharing them.
     */
    public void onReceiptChanged(Supply receipt) {
        Set<Supply> affected = new LinkedHashSet<>();
        affected.add(receipt);
        if (receipt.getCharges() != null) {
            for (SupplyUseCharge link : receipt.getCharges()) {
                SupplyCharge charge = link.getCharge();
                charge.allocate();
                charge.getSupplys().forEach(use -> affected.add(use.getSupply()));
            }
        }
        affected.forEach(Supply::recalculateCosts);
    }

    /** Deletes a charge and takes it out of the cost of the receipts that carried it. */
    public void removeCharge(SupplyCharge charge) {
        List<Supply> affected = charge.getSupplys().stream()
                .map(SupplyUseCharge::getSupply)
                .toList();
        charge.getSupplys().forEach(use -> use.getSupply().getCharges().remove(use));
        chargeRepository.delete(charge);
        affected.forEach(Supply::recalculateCosts);
    }

    /** Totals over receipts, a delivery note for instance. */
    @Transactional(readOnly = true)
    public EntryCostSummary summarize(Collection<Supply> receipts) {
        BigDecimal purchase = receipts.stream()
                .map(Supply::totalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charges = receipts.stream()
                .map(Supply::getChargesCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = receipts.stream().allMatch(s -> s.getUnitCost() != null);
        return EntryCostSummary.builder()
                .purchaseTotal(purchase)
                .chargesTotal(charges)
                .entryTotal(purchase.add(charges))
                .complete(complete)
                .build();
    }

    /** {@code complete} is false when a receipt has no purchase cost: the totals are partial. */
    @Builder
    public record EntryCostSummary(BigDecimal purchaseTotal, BigDecimal chargesTotal,
                                   BigDecimal entryTotal, boolean complete) {
    }
}