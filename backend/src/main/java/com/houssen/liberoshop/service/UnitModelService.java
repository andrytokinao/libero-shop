package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.UnitModelRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateUnitModelRequest;
import com.houssen.liberoshop.web.dto.RenameUnitModelRequest;
import com.houssen.liberoshop.web.dto.UnitModelResponse;
import com.houssen.liberoshop.web.dto.UnitModelUnitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The models of units the shop keeps to reuse: "Poids" = g, kg, tonne.
 *
 * <p>Only the models are written here. Putting a model's units on a product is
 * {@link UnitModelApplication}'s, and never changes the model; changing a model never changes a
 * product either -- what was applied was copied.
 */
@Service
@Transactional(readOnly = true)
public class UnitModelService {

    private final UnitModelRepository models;

    public UnitModelService(UnitModelRepository models) {
        this.models = models;
    }

    public List<UnitModelResponse> findAll() {
        return models.findAllByOrderByNameAsc().stream().map(UnitModelResponse::of).toList();
    }

    /** A new model and, in order, the units it starts with. Nothing is kept if one is refused. */
    @RequiresActiveLicense
    @Transactional
    public UnitModelResponse create(CreateUnitModelRequest request) {
        String name = requireName(request.name());
        if (models.existsByNameIgnoreCase(name)) {
            throw nameTaken(name);
        }
        String base = PackagingRules.squeeze(request.baseLabel());
        if (base == null) {
            throw new BusinessRuleException("UNIT_MODEL_BASE_REQUIRED",
                    "Le modele doit avoir une unite de reference (g, kg, piece...).");
        }
        UnitModel model = UnitModel.builder().name(name).baseLabel(base).build();
        if (request.units() != null) {
            request.units().forEach(unit -> addTo(model, unit));
        }
        return UnitModelResponse.of(models.save(model));
    }

    @RequiresActiveLicense
    @Transactional
    public UnitModelResponse rename(Long modelId, RenameUnitModelRequest request) {
        UnitModel model = require(modelId);
        String name = requireName(request.name());
        if (models.existsByNameIgnoreCaseAndIdNot(name, modelId)) {
            throw nameTaken(name);
        }
        model.setName(name);
        return UnitModelResponse.of(model);
    }

    /** The products it was applied to keep their units: they were copied. */
    @RequiresActiveLicense
    @Transactional
    public void delete(Long modelId) {
        models.delete(require(modelId));
    }

    @RequiresActiveLicense
    @Transactional
    public UnitModelResponse addUnit(Long modelId, UnitModelUnitRequest request) {
        UnitModel model = require(modelId);
        addTo(model, request);
        models.flush();
        return UnitModelResponse.of(model);
    }

    /** Refused while another unit of the model is written in this one, like a product's. */
    @RequiresActiveLicense
    @Transactional
    public UnitModelResponse removeUnit(Long modelId, Long unitId) {
        UnitModel model = require(modelId);
        UnitModelUnit unit = model.getUnits().stream()
                .filter(candidate -> candidate.getId().equals(unitId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Unite du modele", unitId));
        String folded = CsvTable.normalise(unit.getLabel());
        model.getUnits().stream()
                .filter(other -> other.getReferenceLabel() != null
                        && CsvTable.normalise(other.getReferenceLabel()).equals(folded))
                .findFirst()
                .ifPresent(dependent -> {
                    throw new BusinessRuleException("UNIT_MODEL_UNIT_IS_REFERENCE",
                            "\"" + dependent.getLabel() + "\" est ecrite en " + unit.getLabel()
                                    + " : retirez-la d'abord.");
                });
        model.getUnits().remove(unit);
        return UnitModelResponse.of(model);
    }

    // ------------------------------------------------------------------- internals

    /**
     * Checks the unit against the model -- a new name, written in a unit the model already has,
     * a contenance like a product's -- then appends it.
     */
    private static void addTo(UnitModel model, UnitModelUnitRequest request) {
        String label = PackagingRules.squeeze(request.label());
        if (label == null) {
            throw new BusinessRuleException("PACKAGING_LABEL_REQUIRED", "Le nom de l'unite est obligatoire.");
        }
        if (UnitModelFactors.hasUnit(model, label)) {
            throw new BusinessRuleException("UNIT_MODEL_LABEL_TAKEN",
                    "Le modele \"" + model.getName() + "\" a deja une unite \"" + label + "\".");
        }
        String reference = referenceOf(model, request.reference());
        BigDecimal quantity = PackagingRules.validQuantity(request.quantity());
        UnitModelUnit unit = UnitModelUnit.builder()
                .model(model).label(label).quantity(quantity).referenceLabel(reference).build();
        if (UnitModelFactors.factorOf(model, unit).compareTo(BigDecimal.ONE) == 0) {
            throw new BusinessRuleException("UNIT_MODEL_UNIT_IS_BASE",
                    "Une unite qui contient exactement 1 " + model.getBaseLabel()
                            + " est l'unite de reference elle-meme.");
        }
        model.getUnits().add(unit);
    }

    /** The model's own spelling of the reference; null for its reference unit, named or not. */
    private static String referenceOf(UnitModel model, String name) {
        String reference = PackagingRules.squeeze(name);
        if (reference == null
                || CsvTable.normalise(reference).equals(CsvTable.normalise(model.getBaseLabel()))) {
            return null;
        }
        return UnitModelFactors.find(model, reference)
                .map(UnitModelUnit::getLabel)
                .orElseThrow(() -> new BusinessRuleException("UNIT_MODEL_REFERENCE_UNKNOWN",
                        "Le modele \"" + model.getName() + "\" n'a pas d'unite \"" + reference
                                + "\" : ajoutez-la d'abord, ou choisissez un autre modele."));
    }

    private UnitModel require(Long id) {
        return models.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Modele d'unites", id));
    }

    private static String requireName(String value) {
        String name = PackagingRules.squeeze(value);
        if (name == null) {
            throw new BusinessRuleException("UNIT_MODEL_NAME_REQUIRED", "Le nom du modele est obligatoire.");
        }
        return name;
    }

    private static BusinessRuleException nameTaken(String name) {
        return new BusinessRuleException("UNIT_MODEL_NAME_TAKEN", "Un modele s'appelle deja \"" + name + "\".");
    }
}
