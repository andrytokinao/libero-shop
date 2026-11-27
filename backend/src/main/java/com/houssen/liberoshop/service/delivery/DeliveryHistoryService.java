package com.houssen.liberoshop.service.delivery;

import com.houssen.liberoshop.entity.DeliveryNote;
import com.houssen.liberoshop.entity.StockLoss;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.SupplyCharge;
import com.houssen.liberoshop.repository.DeliveryNoteRepository;
import com.houssen.liberoshop.repository.SupplyChargeRepository;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.service.delivery.dto.DeliveryNoteResponse;
import com.houssen.liberoshop.service.delivery.dto.DeliveryNoteSummaryResponse;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The history of the delivery notes booked with charges and losses: the list of the latest ones,
 * and one note read back whole -- its receipts, what each really cost, the charges and how they
 * were shared, the losses on the way.
 *
 * <p>Read only. Receipts carry purchase prices, so the controller restricts it like the other
 * stock screens.
 */
@Service
@Transactional(readOnly = true)
public class DeliveryHistoryService {

    private final DeliveryNoteRepository notes;
    private final SupplyChargeRepository supplyCharges;

    public DeliveryHistoryService(DeliveryNoteRepository notes, SupplyChargeRepository supplyCharges) {
        this.notes = notes;
        this.supplyCharges = supplyCharges;
    }

    /**
     * The latest notes, newest first: the screen shows the recent ones, not years of them.
     *
     * @param supplierId only the notes of this supplier, or null for all of them
     */
    public List<DeliveryNoteSummaryResponse> findNotes(Long supplierId) {
        PageRequest latest = PageRequest.of(0, StockService.LATEST_LIMIT);
        List<DeliveryNote> found = supplierId == null
                ? notes.findLatest(latest)
                : notes.findLatestOf(supplierId, latest);
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(DeliveryNote::getId).toList();
        Map<Long, Long> lines = counts(notes.countLines(ids));
        Map<Long, Long> losses = counts(notes.countLosses(ids));
        return found.stream()
                .map(note -> DeliveryNoteSummaryResponse.of(note,
                        lines.getOrDefault(note.getId(), 0L),
                        losses.getOrDefault(note.getId(), 0L)))
                .toList();
    }

    /** One note, whole. */
    public DeliveryNoteResponse findNote(Long noteId) {
        DeliveryNote note = notes.findWithSupplier(noteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bon de reception", noteId));
        // The receipts first: the charges and the losses then find them already loaded.
        List<Supply> supplies = notes.findSuppliesOf(noteId);
        List<SupplyCharge> charges = supplyCharges.findOfNote(noteId);
        List<StockLoss> losses = notes.findLossesOf(noteId);
        return DeliveryNoteResponse.of(note, supplies, charges, losses);
    }

    /** The note one receipt was booked with, whole: the way in from a line of the supplies list. */
    public DeliveryNoteResponse findNoteOfSupply(Long supplyId) {
        Long noteId = notes.findNoteIdOfSupply(supplyId)
                .orElseThrow(() -> new BusinessRuleException("SUPPLY_WITHOUT_NOTE",
                        "Cette reception a ete saisie seule : elle n'a ni bon ni charges."));
        return findNote(noteId);
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
