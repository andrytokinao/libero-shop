package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.ChargeBasis;
import com.houssen.liberoshop.entity.GeneralCharge;
import com.houssen.liberoshop.entity.LotCharge;
import com.houssen.liberoshop.entity.SupplyCharge;
import com.houssen.liberoshop.entity.UnitCharge;
import com.houssen.liberoshop.service.delivery.dto.SupplyChargeRequest;

/** Turns a charge of the request into the entity class that knows how to split it. */
public final class SupplyChargeMapper {

    private SupplyChargeMapper() {
    }

    public static SupplyCharge toCharge(SupplyChargeRequest request) {
        return builderFor(request)
                .raison(request.raison().trim())
                .cust(request.cust())
                .general(request.general())
                .build();
    }

    private static SupplyCharge.SupplyChargeBuilder<?, ?> builderFor(SupplyChargeRequest request) {
        if (request.basis() == ChargeBasis.UNIT) {
            return UnitCharge.builder();
        }
        // A total: shared when general or naming several lines. validate() ensures that a
        // charge which is not general names at least one line.
        boolean shared = request.general()
                || request.lineIndexes().stream().distinct().count() > 1;
        return shared ? GeneralCharge.builder() : LotCharge.builder();
    }
}
