package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.service.ImportAction;
import com.houssen.liberoshop.service.ProductUnitSheet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line the operator ticked, as they left it.
 *
 * <p>The values are re-sent rather than looked up from a server-side copy of the preview:
 * every one of them is editable in the dialog -- the name because of the rename, the price
 * because a file often has none, the rayon because of the second dialog -- so the preview the
 * server produced is not what is being applied. Nothing is trusted from here: the line is
 * re-matched against the catalogue at apply time.
 *
 * @param line        the file's line number, carried only so the result can name it back
 * @param action      {@code MERGE} to add to {@code mergeIntoId}, {@code CREATE} for a new
 *                    reference
 * @param mergeIntoId which product to merge into; required by {@code MERGE}, ignored otherwise
 * @param categoryId  the chosen rayon, or null
 * @param categoryPath a rayon by name, created if missing -- how a file's own rayons land.
 *                    Only read when {@code categoryId} is null.
 * @param cost        purchase price of one unit, or null. Read for merges as well as
 *                    creations: unlike the sale price, it describes these goods, not the product.
 * @param packagings  the other units the product sells in -- "kg = 3,5 kapoka" -- as the units
 *                    dialog would send them. A merge only adds the ones the product lacks: like its
 *                    name and price, the units a shop already set stand.
 */
public record ProductImportLineRequest(
        int line,

        @NotBlank(message = "le nom du produit est obligatoire")
        @Size(max = 120)
        String name,

        @NotNull
        @PositiveOrZero(message = "la quantite ne peut pas etre negative")
        @Digits(integer = 11, fraction = 3)
        BigDecimal quantity,

        @Size(max = Product.MAX_UNIT_LENGTH)
        String unit,

        @NotNull
        @Positive(message = "le prix de vente doit etre superieur a zero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
        @Digits(integer = 10, fraction = 2)
        BigDecimal cost,

        @Size(max = 64)
        String barcode,

        Long categoryId,

        String categoryPath,

        @NotNull ImportAction action,

        Long mergeIntoId,

        @Size(max = ProductUnitSheet.MAX_UNITS_PER_PRODUCT)
        List<@Valid PackagingRequest> packagings) {

    public ProductImportLineRequest {
        packagings = packagings == null ? List.of() : List.copyOf(packagings);
    }

    /** A line without extra units, as every line was before units. */
    public ProductImportLineRequest(int line, String name, BigDecimal quantity, String unit,
                                    BigDecimal price, BigDecimal cost, String barcode,
                                    Long categoryId, String categoryPath, ImportAction action,
                                    Long mergeIntoId) {
        this(line, name, quantity, unit, price, cost, barcode, categoryId, categoryPath, action,
                mergeIntoId, List.of());
    }
}
