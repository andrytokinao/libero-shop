package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateUnitModelRequest;
import com.houssen.liberoshop.web.dto.RenameUnitModelRequest;
import com.houssen.liberoshop.web.dto.UnitModelResponse;
import com.houssen.liberoshop.web.dto.UnitModelUnitRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A model of units keeps the same rules as a product's units, without prices or stock. */
@SpringBootTest
class UnitModelServiceTest {

    @Autowired
    private UnitModelService service;

    private static String name() {
        return "Modele " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static UnitModelUnitRequest unit(String label, String quantity, String reference) {
        return new UnitModelUnitRequest(label, new BigDecimal(quantity), reference);
    }

    private UnitModelResponse weight() {
        return service.create(new CreateUnitModelRequest(name(), "g",
                List.of(unit("kg", "1000", null), unit("tonne", "1000", "kg"))));
    }

    @Test
    @DisplayName("a unit written in another is counted down to the reference unit: a tonne is 1 000 000 g")
    void uniteEcriteDansUneAutre() {
        UnitModelResponse model = weight();

        assertEquals("kg", model.units().get(1).reference());
        assertEquals(0, new BigDecimal("1000000").compareTo(model.units().get(1).baseFactor()));
    }

    @Test
    @DisplayName("a name already in the model, its reference unit included, is refused folded")
    void nomDejaPris() {
        UnitModelResponse model = weight();

        assertEquals("UNIT_MODEL_LABEL_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.addUnit(model.id(), unit("KG", "2", null))).code());
        assertEquals("UNIT_MODEL_LABEL_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.addUnit(model.id(), unit("G", "2", null))).code());
    }

    @Test
    @DisplayName("a unit is written in the reference unit or in one the model already has")
    void referenceInconnue() {
        UnitModelResponse model = weight();

        assertEquals("UNIT_MODEL_REFERENCE_UNKNOWN", assertThrows(BusinessRuleException.class,
                () -> service.addUnit(model.id(), unit("sac", "25", "kapoka"))).code());
        UnitModelResponse withSack = service.addUnit(model.id(), unit("sac 25 kg", "25", "Kg"));
        assertEquals("kg", withSack.units().getLast().reference());
    }

    @Test
    @DisplayName("a unit holding exactly one reference unit is the reference unit again")
    void uniteEgaleALaReference() {
        UnitModelResponse model = weight();

        assertEquals("UNIT_MODEL_UNIT_IS_BASE", assertThrows(BusinessRuleException.class,
                () -> service.addUnit(model.id(), unit("gramme", "1", null))).code());
    }

    @Test
    @DisplayName("a unit another is written in cannot be removed first")
    void retraitDUneReference() {
        UnitModelResponse model = weight();
        Long kg = model.units().getFirst().id();

        assertEquals("UNIT_MODEL_UNIT_IS_REFERENCE", assertThrows(BusinessRuleException.class,
                () -> service.removeUnit(model.id(), kg)).code());
        UnitModelResponse withoutTonne = service.removeUnit(model.id(), model.units().get(1).id());
        assertEquals(1, withoutTonne.units().size());
    }

    @Test
    @DisplayName("a model is renamed, never to another model's name")
    void renommage() {
        UnitModelResponse first = weight();
        UnitModelResponse second = weight();

        assertEquals("UNIT_MODEL_NAME_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.rename(second.id(), new RenameUnitModelRequest(first.name().toUpperCase()))).code());
        String renamed = name();
        assertEquals(renamed, service.rename(second.id(), new RenameUnitModelRequest(" " + renamed + " ")).name());
        assertTrue(service.findAll().stream().anyMatch(model -> model.name().equals(renamed)));
    }

    @Test
    @DisplayName("a model created with a refused unit is not kept at all")
    void creationToutOuRien() {
        String name = name();
        assertThrows(BusinessRuleException.class, () -> service.create(new CreateUnitModelRequest(name, "g",
                List.of(unit("kg", "1000", null), unit("tonne", "1000", "quintal")))));

        assertTrue(service.findAll().stream().noneMatch(model -> model.name().equals(name)));
    }
}
