package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * How a model's units relate to its reference unit: a tonne is 1 000 000 g in "Poids".
 *
 * <p>Exact: quantities are multiplied, never divided, so no rounding creeps in here. Labels are
 * compared folded, like a product's units ("Kg" is "kg").
 */
public final class UnitModelFactors {

    private UnitModelFactors() {
    }

    /** Reference units in one {@code label}; 1 for the reference unit, empty for an unknown label. */
    public static Optional<BigDecimal> factorOf(UnitModel model, String label) {
        String folded = CsvTable.normalise(label);
        if (CsvTable.normalise(model.getBaseLabel()).equals(folded)) {
            return Optional.of(BigDecimal.ONE);
        }
        return find(model, label).map(unit -> factorOf(model, unit));
    }

    /** The unit's quantity times its reference's, down to the model's reference unit. */
    public static BigDecimal factorOf(UnitModel model, UnitModelUnit unit) {
        return unit.getReferenceLabel() == null
                ? unit.getQuantity()
                : unit.getQuantity().multiply(factorOf(model, unit.getReferenceLabel()).orElse(BigDecimal.ONE));
    }

    /** One of the model's units, its reference unit excluded. */
    public static Optional<UnitModelUnit> find(UnitModel model, String label) {
        String folded = CsvTable.normalise(label);
        return model.getUnits().stream()
                .filter(unit -> CsvTable.normalise(unit.getLabel()).equals(folded))
                .findFirst();
    }

    /** Whether the model names a unit so, its reference unit included. */
    public static boolean hasUnit(UnitModel model, String label) {
        return factorOf(model, label).isPresent();
    }
}
