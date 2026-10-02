package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<ProductPackaging> siblings = packagings.findByProductIdOrderByFactorAsc(product.getId());
        refuseLabelTaken(label, product, packaging, false);
        ProductPackaging reference = referenceOf(request.reference(), product, packaging, siblings);
        BigDecimal quantity = validQuantity(request.factor());
        BigDecimal factor = validFactor(
                quantity.multiply(reference == null ? BigDecimal.ONE : reference.getFactor()),
                product.getUnit());
        BigDecimal price = validPrice(request.price());
        String barcode = squeeze(request.barcode());
        if (barcode != null && !barcode.equals(packaging.getBarcode())) {
            refuseBarcodeTaken(barcode);
        }
        // Checked before anything is written: a kilo cannot become 3.6 kapoka if that turns a
        // sack written in kilos into a factor no shelf can count.
        Map<ProductPackaging, BigDecimal> following = followersOf(packaging, factor, siblings);

        packaging.setLabel(label);
        packaging.setReference(reference);
        packaging.setReferenceQuantity(quantity);
        packaging.setFactor(factor);
        packaging.setPrice(price);
        packaging.setBarcode(barcode);
        following.forEach(ProductPackaging::setFactor);
    }

    /**
     * Refuses removing a unit another one is written in: the sack written as "25 kg" would be
     * left saying 25 of nothing. The operator rewrites or removes the sack first -- a decision
     * the application does not take for them.
     */
    public void refuseRemoval(ProductPackaging packaging) {
        packagings.findByProductIdOrderByFactorAsc(packaging.getProduct().getId()).stream()
                .filter(other -> other.getReference() != null
                        && Objects.equals(other.getReference().getId(), packaging.getId()))
                .findFirst()
                .ifPresent(dependent -> {
                    throw new BusinessRuleException("PACKAGING_IS_REFERENCE",
                            "\"" + dependent.getLabel() + "\" est ecrit en " + packaging.getLabel()
                                    + " : changez son unite de reference ou supprimez-le d'abord.");
                });
    }

    /**
     * The unit a request is written in: null for the base unit -- named or left blank -- else
     * one of the product's units, which must not be this one nor written in it.
     */
    private static ProductPackaging referenceOf(String name, Product product, ProductPackaging self,
                                                List<ProductPackaging> siblings) {
        String reference = squeeze(name);
        if (reference == null || (product.getUnit() != null
                && CsvTable.normalise(product.getUnit()).equals(CsvTable.normalise(reference)))) {
            return null;
        }
        ProductPackaging found = siblings.stream()
                .filter(other -> CsvTable.normalise(other.getLabel()).equals(CsvTable.normalise(reference)))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("PACKAGING_REFERENCE_UNKNOWN",
                        "L'unite de reference \"" + reference + "\" n'existe pas pour \""
                                + product.getName() + "\" : creez-la d'abord."));
        if (self.getId() != null && self.underlies(found)) {
            throw new BusinessRuleException("PACKAGING_REFERENCE_CYCLE",
                    "\"" + found.getLabel() + "\" ne peut pas servir de reference : elle est "
                            + "elle-meme ecrite a partir de cette unite.");
        }
        return found;
    }

    /**
     * Every unit written, directly or through others, in {@code changed}, with the factor it
     * takes once {@code changed} holds {@code factor} base units -- each one checked like a unit
     * typed by hand, and refused naming it.
     */
    private static Map<ProductPackaging, BigDecimal> followersOf(ProductPackaging changed, BigDecimal factor,
                                                                 List<ProductPackaging> siblings) {
        Map<ProductPackaging, BigDecimal> following = new LinkedHashMap<>();
        if (changed.getId() == null) {
            return following;
        }
        Deque<Map.Entry<ProductPackaging, BigDecimal>> todo = new ArrayDeque<>();
        todo.add(Map.entry(changed, factor));
        while (!todo.isEmpty()) {
            Map.Entry<ProductPackaging, BigDecimal> parent = todo.poll();
            for (ProductPackaging child : siblings) {
                if (child.getReference() == null
                        || !Objects.equals(child.getReference().getId(), parent.getKey().getId())
                        || following.containsKey(child)) {
                    continue;
                }
                BigDecimal childFactor;
                try {
                    childFactor = validFactor(child.quantityInReference().multiply(parent.getValue()),
                            changed.getProduct().getUnit());
                } catch (BusinessRuleException refused) {
                    throw new BusinessRuleException(refused.code(),
                            "\"" + child.getLabel() + "\", ecrite a partir de cette unite : "
                                    + refused.getMessage());
                }
                following.put(child, childFactor);
                todo.add(Map.entry(child, childFactor));
            }
        }
        return following;
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

    /**
     * A contenance as typed: "25" in "sac 25 kg = 25 kg". Positive, at most
     * {@value ProductPackaging#FACTOR_SCALE} decimals -- the factor it leads to is judged by
     * {@link #validFactor} once the reference is known.
     */
    public static BigDecimal validQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("PACKAGING_FACTOR_INVALID",
                    "La contenance doit etre superieure a zero.");
        }
        if (quantity.stripTrailingZeros().scale() > ProductPackaging.FACTOR_SCALE) {
            throw new BusinessRuleException("PACKAGING_FACTOR_TOO_PRECISE",
                    "La contenance accepte au plus " + ProductPackaging.FACTOR_SCALE + " decimales.");
        }
        return quantity.setScale(ProductPackaging.FACTOR_SCALE);
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
