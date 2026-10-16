package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.UnitModel;
import com.houssen.liberoshop.entity.UnitModelUnit;
import com.houssen.liberoshop.repository.UnitModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The "Poids" model is given to a shop that has no model, and to no other. */
class UnitModelSeedTest {

    private final UnitModelRepository models = mock(UnitModelRepository.class);
    private final UnitModelSeed seed = new UnitModelSeed(models);

    @Test
    @DisplayName("a shop without any model gets Poids: g, kg = 1000 g, tonne = 1000 kg")
    void modelePoidsCreeSiAucunModele() {
        when(models.count()).thenReturn(0L);

        seed.run(null);

        ArgumentCaptor<UnitModel> saved = ArgumentCaptor.forClass(UnitModel.class);
        verify(models).save(saved.capture());
        UnitModel weight = saved.getValue();
        assertEquals("Poids", weight.getName());
        assertEquals("g", weight.getBaseLabel());
        assertEquals(java.util.List.of("kg", "tonne"), weight.getUnits().stream().map(UnitModelUnit::getLabel).toList());
        assertEquals("kg", weight.getUnits().get(1).getReferenceLabel());
    }

    @Test
    @DisplayName("a shop that already has a model, renamed or its own, is left alone")
    void rienSiUnModeleExiste() {
        when(models.count()).thenReturn(1L);

        seed.run(null);

        verify(models, never()).save(any());
    }
}
