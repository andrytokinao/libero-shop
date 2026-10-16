package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;
import com.houssen.liberoshop.service.UnitModelFactors;

import java.math.BigDecimal;
import java.util.List;

/**
 * A model of units as the screens show it.
 *
 * @param units in the order they were added, which is an order they can be created in
 */
public record UnitModelResponse(Long id, String name, String baseLabel, List<Unit> units) {

    /**
     * @param reference  the unit it is written in, null for the model's reference unit
     * @param baseFactor how many reference units of the model it holds: 1 000 000 for a tonne in g
     */
    public record Unit(Long id, String label, BigDecimal quantity, String reference, BigDecimal baseFactor) {
    }

    public static UnitModelResponse of(UnitModel model) {
        return new UnitModelResponse(model.getId(), model.getName(), model.getBaseLabel(),
                model.getUnits().stream().map(unit -> unitOf(model, unit)).toList());
    }

    private static Unit unitOf(UnitModel model, UnitModelUnit unit) {
        return new Unit(unit.getId(), unit.getLabel(), unit.getQuantity().stripTrailingZeros(),
                unit.getReferenceLabel(), UnitModelFactors.factorOf(model, unit).stripTrailingZeros());
    }
}
