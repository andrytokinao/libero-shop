package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.web.dto.CreateDeliveryRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A whole delivery note booked at once: ten products from one supplier, one "Enregistrer".
 *
 * <p>Each line is a receipt like any other ({@link StockService#registerSupply}): the same unit
 * resolution, the same cost averaging, the same movement in the product's history. What this adds
 * is that they stand or fall together: a line refused -- an unknown product, a quantity too
 * precise -- leaves the stock as it was, so a note is never half booked.
 *
 * <p>The products are locked in the order of their ids, as a sale does, so two deliveries
 * sharing products cannot wait on each other.
 */
@Service
public class DeliveryService {

    private final StockService stock;

    public DeliveryService(StockService stock) {
        this.stock = stock;
    }

    /** @return the receipts, in the order of the note's lines */
    @RequiresActiveLicense
    @Transactional
    public List<SupplyResponse> receive(CreateDeliveryRequest request, UserApp receiver) {
        List<CreateDeliveryRequest.Line> lines = request.lines();
        SupplyResponse[] booked = new SupplyResponse[lines.size()];
        IntStream.range(0, lines.size()).boxed()
                .sorted(Comparator.comparing(i -> lines.get(i).productId()))
                .forEach(i -> booked[i] = stock.registerSupply(asSupply(request.supplierId(), lines.get(i)), receiver));
        return new ArrayList<>(Arrays.asList(booked));
    }

    private static CreateSupplyRequest asSupply(Long supplierId, CreateDeliveryRequest.Line line) {
        return new CreateSupplyRequest(line.productId(), supplierId, line.packagingId(), line.quantity(),
                line.unitCost());
    }
}
