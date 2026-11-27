package com.houssen.liberoshop.service.delivery.dto;

import com.houssen.liberoshop.entity.DeliveryNote;
import com.houssen.liberoshop.entity.StockLoss;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.SupplyCharge;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A delivery note read back whole: the receipts with what each really cost, the charges with the
 * part each receipt carries, and what was lost on the way.
 */
public record DeliveryNoteResponse(
        Long id,
        LocalDateTime date,
        Long supplierId,
        String supplierName,
        Long performedById,
        BigDecimal purchaseTotal,
        BigDecimal chargesTotal,
        BigDecimal grandTotal,
        boolean withTransformation,
        boolean cancelled,
        List<Line> lines,
        List<Charge> charges,
        List<Loss> losses
) {

    /** One receipt of the note. */
    public record Line(
            Long supplyId,
            Long productId,
            String productName,
            String unitLabel,              // the unit it was received in: "sac 50 kg", or the base unit
            BigDecimal receivedQuantity,   // in that unit
            BigDecimal baseQuantity,       // in the product's base unit
            BigDecimal unitCost,           // per unit received, as invoiced; null when unknown
            BigDecimal purchaseTotal,      // null when unknown
            BigDecimal lostBaseQuantity,   // lost on the way, in base units
            BigDecimal chargesShare,       // the part of the charges this receipt carries
            BigDecimal landedBaseUnitCost, // what one usable base unit really cost; null when unknown
            boolean cancelled
    ) {
        static Line of(Supply supply) {
            String unit = supply.getUnitLabel() != null ? supply.getUnitLabel() : supply.getProduct().getUnit();
            return new Line(
                    supply.getId(),
                    supply.getProduct().getId(),
                    supply.getProduct().getName(),
                    unit,
                    supply.receivedQuantity(),
                    supply.getQuantity(),
                    supply.getUnitCost(),
                    supply.totalCost(),
                    supply.lostQuantity(),
                    supply.chargesTotal(),
                    supply.landedBaseUnitCost(),
                    supply.isCancelled());
        }
    }

    /** One charge, and how it was spread over the receipts. */
    public record Charge(
            Long id,
            String raison,
            BigDecimal cust,
            boolean general,
            String basis,                  // "lot" or "unit": how it was typed
            List<Share> shares
    ) {
        static Charge of(SupplyCharge charge) {
            return new Charge(
                    charge.getId(),
                    charge.getRaison(),
                    charge.getCust(),
                    charge.isGeneral(),
                    charge.getBasis(),
                    charge.getSupplys().stream()
                            .map(use -> new Share(
                                    use.getSupply().getId(),
                                    use.getSupply().getProduct().getName(),
                                    use.getShare()))
                            .toList());
        }
    }

    /** The part of a charge carried by one receipt. */
    public record Share(Long supplyId, String productName, BigDecimal share) {}

    /** A loss on the way: a quantity, never a price. */
    public record Loss(
            Long id,
            Long supplyId,
            Long productId,
            String productName,
            String reason,                 // TRANSPORT, SPOILAGE, OTHER
            BigDecimal quantity,           // in base units
            LocalDateTime date,
            boolean cancelled
    ) {
        static Loss of(StockLoss loss) {
            return new Loss(
                    loss.getId(),
                    loss.getSupply() == null ? null : loss.getSupply().getId(),
                    loss.getProduct().getId(),
                    loss.getProduct().getName(),
                    loss.getReason() == null ? null : loss.getReason().name(),
                    loss.getQuantity(),
                    loss.getMovementDate(),
                    loss.isCancelled());
        }
    }

    public static DeliveryNoteResponse of(DeliveryNote note, List<Supply> supplies,
                                          List<SupplyCharge> charges, List<StockLoss> losses) {
        BigDecimal purchase = note.getPurchaseTotal() == null ? BigDecimal.ZERO : note.getPurchaseTotal();
        BigDecimal chargesTotal = note.getChargesTotal() == null ? BigDecimal.ZERO : note.getChargesTotal();
        return new DeliveryNoteResponse(
                note.getId(),
                note.getDate(),
                note.getSupplier() == null ? null : note.getSupplier().getId(),
                note.getSupplier() == null ? null : note.getSupplier().getName(),
                note.getPerformedBy().getId(),
                purchase,
                chargesTotal,
                purchase.add(chargesTotal),
                note.isWithTransformation(),
                note.isCancelled(),
                supplies.stream().map(Line::of).toList(),
                charges.stream().map(Charge::of).toList(),
                losses.stream().map(Loss::of).toList());
    }
}
