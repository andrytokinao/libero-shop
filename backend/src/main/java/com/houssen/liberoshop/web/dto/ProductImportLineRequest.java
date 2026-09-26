package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.service.ProductImportAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
 */
public record ProductImportLineRequest(
        int line,

        @NotBlank(message = "le nom du produit est obligatoire")
        @Size(max = 120)
        String name,

        @PositiveOrZero(message = "la quantite ne peut pas etre negative")
        int quantity,

        @Size(max = Product.MAX_UNIT_LENGTH)
        String unit,

        @NotNull
        @PositiveOrZero(message = "le prix ne peut pas etre negatif")
        BigDecimal price,

        @Size(max = 64)
        String barcode,

        Long categoryId,

        String categoryPath,

        @NotNull ProductImportAction action,

        Long mergeIntoId) {
}
