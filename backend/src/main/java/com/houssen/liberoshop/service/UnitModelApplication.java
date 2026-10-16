package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.UnitModelRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ApplyUnitModelRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Puts units ticked in a model onto a product, as ordinary packagings at the product's prices.
 *
 * <p>The model only says how its units relate to each other ("tonne = 1000 kg"). Where it meets
 * the product is found, in this order:
 * <ol>
 *   <li>the product is counted in one of the model's units (a product counted in kg);</li>
 *   <li>the product already sells in one of them (rice in kapoka that has its "kg = 3,5 kapoka");</li>
 *   <li>the operator said it: "1 kg = 3,5 kapoka" (the anchor).</li>
 * </ol>
 *
 * <p>Each unit is written the way the model writes it whenever the product has its reference
 * unit -- the tonne as "1000 kg" -- so that it follows the product's kg if that is corrected
 * later; otherwise directly in base units. Every unit then goes through {@link PackagingRules},
 * like one typed by hand: a gram of rice counted in kapoka (0,0035) is refused, not rounded.
 *
 * <p>Not transactional, like {@link PackagingRules}: called inside the caller's transaction --
 * adding units to a product, or creating it -- which a refusal rolls back whole.
 */
@Component
public class UnitModelApplication {

    private static final MathContext EXACT_ENOUGH = MathContext.DECIMAL64;

    private final UnitModelRepository models;
    private final ProductPackagingRepository packagings;
    private final PackagingRules rules;

    public UnitModelApplication(UnitModelRepository models, ProductPackagingRepository packagings,
                                PackagingRules rules) {
        this.models = models;
        this.packagings = packagings;
        this.rules = rules;
    }

    /**
     * @param product saved already: its units are looked up by its id
     * @throws BusinessRuleException naming the unit refused and why; nothing is kept then
     */
    public void applyTo(Product product, ApplyUnitModelRequest request) {
        UnitModel model = models.findById(request.modelId())
                .orElseThrow(() -> ResourceNotFoundException.of("Modele d'unites", request.modelId()));
        Map<String, BigDecimal> prices = pricesByLabel(model, request);
        BigDecimal perModelUnit = baseUnitsPerModelUnit(product, model, request);

        // The reference unit first, then the model's order: each unit after the one it is written in.
        for (String label : candidateLabels(model)) {
            BigDecimal price = prices.get(CsvTable.normalise(label));
            if (price == null) {
                continue;
            }
            if (rules.hasUnit(product, label)) {
                throw new BusinessRuleException("UNIT_MODEL_UNIT_PRESENT",
                        "\"" + product.getName() + "\" a deja une unite \"" + label + "\".");
            }
            add(product, requestFor(product, model, label, price, perModelUnit));
        }
    }

    // ------------------------------------------------------------------- internals

    /** The ticked units, by folded label; refuses one the model does not have, or the same twice. */
    private static Map<String, BigDecimal> pricesByLabel(UnitModel model, ApplyUnitModelRequest request) {
        Set<String> seen = new HashSet<>();
        for (ApplyUnitModelRequest.Choice choice : request.units()) {
            if (!UnitModelFactors.hasUnit(model, choice.label())) {
                throw new BusinessRuleException("UNIT_MODEL_UNIT_UNKNOWN",
                        "Le modele \"" + model.getName() + "\" n'a pas d'unite \"" + choice.label() + "\".");
            }
            if (!seen.add(CsvTable.normalise(choice.label()))) {
                throw new BusinessRuleException("UNIT_MODEL_UNIT_TWICE",
                        "L'unite \"" + choice.label() + "\" est cochee deux fois.");
            }
        }
        return request.units().stream().collect(Collectors.toMap(
                choice -> CsvTable.normalise(choice.label()), ApplyUnitModelRequest.Choice::price));
    }

    /** How many of the product's base units one reference unit of the model holds. */
    private BigDecimal baseUnitsPerModelUnit(Product product, UnitModel model, ApplyUnitModelRequest request) {
        if (product.getUnit() != null) {
            Optional<BigDecimal> base = UnitModelFactors.factorOf(model, product.getUnit());
            if (base.isPresent()) {
                return BigDecimal.ONE.divide(base.get(), EXACT_ENOUGH);
            }
        }
        Map<String, ProductPackaging> owned = packagings.findByProductIdOrderByFactorAsc(product.getId()).stream()
                .collect(Collectors.toMap(p -> CsvTable.normalise(p.getLabel()), Function.identity(), (a, b) -> a));
        Optional<BigDecimal> known = candidateLabels(model).stream()
                .filter(label -> owned.containsKey(CsvTable.normalise(label)))
                .findFirst()
                .map(label -> owned.get(CsvTable.normalise(label)).getFactor()
                        .divide(UnitModelFactors.factorOf(model, label).orElseThrow(), EXACT_ENOUGH));
        if (known.isPresent()) {
            return known.get();
        }
        return anchored(product, model, request);
    }

    /** "1 kg = 3,5 kapoka", as the operator typed it. */
    private static BigDecimal anchored(Product product, UnitModel model, ApplyUnitModelRequest request) {
        String baseName = product.getUnit() == null ? "unites" : product.getUnit();
        if (request.anchorLabel() == null || request.anchorQuantity() == null) {
            throw new BusinessRuleException("UNIT_MODEL_ANCHOR_REQUIRED",
                    "Indiquez combien de " + baseName + " contient une unite du modele \""
                            + model.getName() + "\" (par exemple 1 kg = 3,5 " + baseName + ").");
        }
        BigDecimal modelFactor = UnitModelFactors.factorOf(model, request.anchorLabel())
                .orElseThrow(() -> new BusinessRuleException("UNIT_MODEL_UNIT_UNKNOWN",
                        "Le modele \"" + model.getName() + "\" n'a pas d'unite \"" + request.anchorLabel() + "\"."));
        BigDecimal quantity = PackagingRules.validQuantity(request.anchorQuantity());
        return quantity.divide(modelFactor, EXACT_ENOUGH);
    }

    /** The reference unit, then the others in order. */
    private static List<String> candidateLabels(UnitModel model) {
        return Stream.concat(
                Stream.of(model.getBaseLabel()),
                model.getUnits().stream().map(UnitModelUnit::getLabel)).toList();
    }

    /**
     * In the model's reference unit when the product has it -- the tonne stays "1000 kg" -- else
     * straight in base units.
     */
    private PackagingRequest requestFor(Product product, UnitModel model, String label,
                                        BigDecimal price, BigDecimal perModelUnit) {
        Optional<UnitModelUnit> unit = UnitModelFactors.find(model, label);
        if (unit.isPresent()) {
            String reference = unit.get().getReferenceLabel() != null
                    ? unit.get().getReferenceLabel() : model.getBaseLabel();
            if (rules.hasUnit(product, reference)) {
                return new PackagingRequest(unit.get().getLabel(), unit.get().getQuantity(), price, null, reference);
            }
        }
        // The model's reference unit, or a unit whose reference the product does not sell in.
        BigDecimal factor = UnitModelFactors.factorOf(model, label).orElseThrow()
                .multiply(perModelUnit, EXACT_ENOUGH).stripTrailingZeros();
        return new PackagingRequest(unit.map(UnitModelUnit::getLabel).orElse(model.getBaseLabel()),
                factor, price, null, null);
    }

    private void add(Product product, PackagingRequest request) {
        ProductPackaging packaging = ProductPackaging.builder().product(product).build();
        try {
            rules.applyTo(request, packaging);
        } catch (BusinessRuleException refused) {
            throw new BusinessRuleException(refused.code(),
                    "\"" + request.label() + "\" : " + refused.getMessage());
        }
        packagings.save(packaging);
    }
}
