package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What makes a product's unit acceptable -- in one place, whoever writes it.
 *
 * <p>Two callers write units: the units dialog, one at a time, and the product import, by the
 * hundred. They must refuse the same things, so the rules live here rather than in either.
 *
 * <p>Deliberately not transactional and not a service: a refusal is an exception, and one thrown
 * across a {@code @Transactional} proxy marks the caller's whole transaction for rollback. The
 * import catches a refused unit, notes it on its line and carries on with the rest of the file --
 * which it can only do if the refusal never crossed such a boundary.
 */
@Component
public class PackagingRules {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;

    public PackagingRules(ProductRepository products, ProductPackagingRepository packagings) {
        this.products = products;
        this.packagings = packagings;
    }

    /**
     * Validates the whole request against its product, then writes it on the row. Nothing is
     * written unless everything passes, so a refusal leaves the row as it was.
     *
     * @param packaging a new row (no id yet) or an existing one being edited; its product is set
     * @throws BusinessRuleException naming what to fix, in French
     */
    public void applyTo(PackagingRequest request, ProductPackaging packaging) {
        Product product = packaging.getProduct();
        String label = squeeze(request.label());
        if (label == null) {
            throw new BusinessRuleException("PACKAGING_LABEL_REQUIRED",
                    "Le nom de l'unite est obligatoire.");
        }
        refuseLabelTaken(label, product, packaging, false);
        BigDecimal factor = validFactor(request.factor(), product.getUnit());
        BigDecimal price = validPrice(request.price());
        String barcode = squeeze(request.barcode());
        if (barcode != null && !barcode.equals(packaging.getBarcode())) {
            refuseBarcodeTaken(barcode);
        }

        packaging.setLabel(label);
        packaging.setFactor(factor);
        packaging.setPrice(price);
        packaging.setBarcode(barcode);
    }

    /** Refuses a base unit named like one of the product's packagings. */
    public void refuseBaseUnitTaken(Product product, String unit) {
        refuseLabelTaken(unit, product, null, true);
    }

    /**
     * One code, one thing to sell -- across products and packagings alike, because the scanner
     * at the till looks in both and must never have to choose.
     */
    public boolean barcodeTaken(String barcode) {
        return products.existsByBarcode(barcode) || packagings.existsByBarcode(barcode);
    }

    /** Whether the product already sells in a unit of that name, base unit included, folded. */
    public boolean hasUnit(Product product, String label) {
        String folded = CsvTable.normalise(label);
        return (product.getUnit() != null && CsvTable.normalise(product.getUnit()).equals(folded))
                || packagings.findByProductIdOrderByFactorAsc(product.getId()).stream()
                        .anyMatch(other -> CsvTable.normalise(other.getLabel()).equals(folded));
    }

    /**
     * Positive, at most {@value ProductPackaging#FACTOR_SCALE} decimals, and not 1.
     *
     * <p>Extra decimals are refused rather than rounded: a factor is what the stock will move
     * by on every sale, and a silently rounded one drifts the count a little each time. Needing
     * them is the sign the base unit is the larger one -- a kapoka is 0.2857... kg, while a kg
     * is exactly 3.5 kapoka.
     *
     * <p>A factor of 1 is the base unit again under another name: two buttons for the same
     * thing at the till, priced differently by accident sooner or later.
     *
     * <p>Static, so a file can be checked line by line before anything is written.
     *
     * @param baseUnit the base unit's name for the message, null for bare units
     */
    public static BigDecimal validFactor(BigDecimal factor, String baseUnit) {
        if (factor == null || factor.signum() <= 0) {
            throw new BusinessRuleException("PACKAGING_FACTOR_INVALID",
                    "La contenance doit etre superieure a zero.");
        }
        BigDecimal stripped = factor.stripTrailingZeros();
        if (stripped.scale() > ProductPackaging.FACTOR_SCALE) {
            throw new BusinessRuleException("PACKAGING_FACTOR_TOO_PRECISE",
                    "La contenance accepte au plus " + ProductPackaging.FACTOR_SCALE
                            + " decimales. Si elle en demande plus, l'unite de base devrait "
                            + "etre la plus petite des deux.");
        }
        if (stripped.compareTo(BigDecimal.ONE) == 0) {
            throw new BusinessRuleException("PACKAGING_FACTOR_IS_BASE",
                    "Une unite qui contient exactement 1 " + (baseUnit == null ? "unite" : baseUnit)
                            + " est l'unite de base elle-meme.");
        }
        return factor.setScale(ProductPackaging.FACTOR_SCALE);
    }

    public static BigDecimal validPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessRuleException("PACKAGING_PRICE_INVALID",
                    "Le prix de l'unite doit etre superieur a zero.");
        }
        return price;
    }

    /** Trimmed and squeezed, null when nothing is left; the operator's capitals are kept. */
    public static String squeeze(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Unique within the product, base unit included, compared folded: "Sac 50 kg" and
     * "sac50kg" would be two identical buttons at the till.
     *
     * @param self the packaging being renamed, left out of the comparison; null when the label
     *             is a new packaging's or the base unit's
     * @param base whether the label is the base unit's, which is then not compared to itself
     */
    private void refuseLabelTaken(String label, Product product, ProductPackaging self, boolean base) {
        String folded = CsvTable.normalise(label);
        Long selfId = self == null ? null : self.getId();
        boolean takenByBase = !base && product.getUnit() != null
                && CsvTable.normalise(product.getUnit()).equals(folded);
        boolean takenByPackaging = packagings.findByProductIdOrderByFactorAsc(product.getId()).stream()
                .filter(other -> !Objects.equals(other.getId(), selfId))
                .anyMatch(other -> CsvTable.normalise(other.getLabel()).equals(folded));
        if (takenByBase || takenByPackaging) {
            throw new BusinessRuleException("PACKAGING_LABEL_TAKEN",
                    "\"" + product.getName() + "\" a deja une unite \"" + label + "\".");
        }
    }

    private void refuseBarcodeTaken(String barcode) {
        if (barcodeTaken(barcode)) {
            throw new BusinessRuleException("BARCODE_TAKEN",
                    "Le code-barres " + barcode + " est deja utilise par un autre article.");
        }
    }
}
