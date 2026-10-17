package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.ImportAction;
import com.houssen.liberoshop.web.dto.ApplyUnitModelRequest;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductImportLineRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every amount and quantity a screen sends is bounded by what the database keeps: refused, not a 500. */
class AmountBoundsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("une quantite vendue avec trop de chiffres est refusee a l'entree")
    void quantiteVendueBornee() {
        assertFalse(validator.validate(new CreateSaleRequest.Line(1L, new BigDecimal("1000000000000"))).isEmpty());
        assertTrue(validator.validate(new CreateSaleRequest.Line(1L, new BigDecimal("999999999.5"))).isEmpty());
    }

    @Test
    @DisplayName("un prix d'unite trop grand est refuse a l'entree")
    void prixDUniteBorne() {
        assertFalse(validator.validate(new PackagingRequest("sac", new BigDecimal("50"),
                new BigDecimal("100000000000"), null)).isEmpty());
        assertFalse(validator.validate(new ApplyUnitModelRequest.Choice("kg", new BigDecimal("100000000000"))).isEmpty());
    }

    @Test
    @DisplayName("l'import refuse un prix de vente a zero")
    void importSansPrixRefuse() {
        ProductImportLineRequest atZero = new ProductImportLineRequest(2, "Riz", new BigDecimal("10"), "sac",
                BigDecimal.ZERO, null, null, null, null, ImportAction.CREATE, null);
        ProductImportLineRequest priced = new ProductImportLineRequest(2, "Riz", new BigDecimal("10"), "sac",
                new BigDecimal("12500"), null, null, null, null, ImportAction.CREATE, null);

        assertFalse(validator.validate(atZero).isEmpty());
        assertTrue(validator.validate(priced).isEmpty());
    }
}
