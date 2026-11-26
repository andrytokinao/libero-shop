package com.houssen.liberoshop.service.delivery;

import com.houssen.liberoshop.entity.DeliveryNote;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.Transformation;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.service.delivery.dto.LossRequest;
import com.houssen.liberoshop.service.delivery.dto.TransformationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A ADAPTER : import de BusinessRuleException (meme package que dans StockService)
import com.houssen.liberoshop.exception.BusinessRuleException;

import java.util.List;

/**
 * Materials turned into finished products. Only declared for now: the delivery note calls it when
 * the request carries a transformation, and until it is written the whole note is refused -- the
 * transaction rolls back, nothing is half booked.
 */
@Service
public class TransformationService {

    /**
     * To write later. The plan, in one transaction:
     * <ol>
     *   <li>for each input: a {@code Consumption} of (quantity used - its process loss), at the
     *       landed cost of the receipt it came from;</li>
     *   <li>for each process loss: a {@code StockLoss(PROCESS)} linked to the transformation;</li>
     *   <li>{@code inputCost} = what the quantities used are worth; {@code productionCost} = the sum
     *       of the production costs; {@code totalCost} = both;</li>
     *   <li>for each output: a {@code Production} with {@code costShare} % of {@code totalCost}
     *       spread over its quantity, blended into the product average cost.</li>
     * </ol>
     *
     * @param note          the delivery note the materials came with
     * @param request       inputs, production costs and outputs, as typed
     * @param processLosses the losses of reason PROCESS, on the note lines
     * @param receipts      the receipts booked for the note, in the order of its lines
     */
    @Transactional
    public Transformation transform(DeliveryNote note,
                                    TransformationRequest request,
                                    List<LossRequest> processLosses,
                                    Supply[] receipts,
                                    UserApp performer) {
        throw new BusinessRuleException("TRANSFORMATION_NOT_AVAILABLE",
                "La transformation n est pas encore disponible : decochez-la pour enregistrer l achat.");
    }
}
