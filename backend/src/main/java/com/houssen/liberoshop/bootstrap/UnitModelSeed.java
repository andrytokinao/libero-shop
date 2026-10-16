package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;
import com.houssen.liberoshop.repository.UnitModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Gives a shop with no model of units the one nearly every shop needs: "Poids" -- g, kg, tonne.
 *
 * <p>Only while there is no model at all, on a new installation as on an updated one. A shop that
 * renamed it keeps its name; one that deleted every model gets it back at the next start, which
 * costs nothing: a model changes no product until it is applied.
 *
 * <p>Written through the repository, not {@code UnitModelService}: its writes need an active
 * licence, and a start with an expired one must not fail over a list of units.
 */
@Component
public class UnitModelSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UnitModelSeed.class);

    static final String WEIGHT = "Poids";

    private final UnitModelRepository models;

    public UnitModelSeed(UnitModelRepository models) {
        this.models = models;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (models.count() > 0) {
            return;
        }
        UnitModel weight = UnitModel.builder().name(WEIGHT).baseLabel("g").build();
        weight.getUnits().add(unit(weight, "kg", "1000", null));
        weight.getUnits().add(unit(weight, "tonne", "1000", "kg"));
        models.save(weight);
        log.info("Modele d'unites \"{}\" (g, kg, tonne) cree.", WEIGHT);
    }

    private static UnitModelUnit unit(UnitModel model, String label, String quantity, String reference) {
        return UnitModelUnit.builder().model(model).label(label)
                .quantity(new BigDecimal(quantity).setScale(3)).referenceLabel(reference).build();
    }
}
