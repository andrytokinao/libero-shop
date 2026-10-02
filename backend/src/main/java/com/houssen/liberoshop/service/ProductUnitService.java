package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.BaseUnitRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The units a product is sold in: its base unit, and the packagings counted in it.
 *
 * <p>The stock never moves here. Renaming the base unit or adding "kg = 3.5 kapoka" changes how
 * the same quantity is described and sold, not how much there is -- which is why none of this
 * goes through {@code StockService}.
 *
 * <p>Writes are licensed, reads are not, like the rest of the catalogue.
 */
@Service
@Transactional(readOnly = true)
public class ProductUnitService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;

    public ProductUnitService(ProductRepository products, ProductPackagingRepository packagings) {
        this.products = products;
        this.packagings = packagings;
    }

    public ProductUnitsResponse unitsOf(Long productId) {
        return responseFor(requireProduct(productId));
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse renameBaseUnit(Long productId, BaseUnitRequest request) {
        Product product = requireProduct(productId);
        String unit = squeeze(request.unit());
        if (unit != null) {
            refuseLabelTaken(unit, product, null, true);
        }
        product.setUnit(unit);
        return responseFor(product);
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse add(Long productId, PackagingRequest request) {
        Product product = requireProduct(productId);
        ProductPackaging packaging = ProductPackaging.builder().product(product).build();
        apply(request, packaging);
        packagings.save(packaging);
        return responseFor(product);
    }

    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse update(Long productId, Long packagingId, PackagingRequest request) {
        ProductPackaging packaging = requirePackaging(productId, packagingId);
        apply(request, packaging);
        return responseFor(packaging.getProduct());
    }

    /**
     * Safe at any time: a sale line copies the label, factor and price it was sold at, so
     * removing a unit never rewrites a past ticket.
     */
    @RequiresActiveLicense
    @Transactional
    public ProductUnitsResponse remove(Long productId, Long packagingId) {
        ProductPackaging packaging = requirePackaging(productId, packagingId);
        Product product = packaging.getProduct();
        packagings.delete(packaging);
        packagings.flush();
        return responseFor(product);
    }

    // ------------------------------------------------------------------- internals

    /** Validates the whole request before touching the row, so a refusal leaves it as it was. */
    private void apply(PackagingRequest request, ProductPackaging packaging) {
        Product product = packaging.getProduct();
        String label = squeeze(request.label());
        if (label == null) {
            throw new BusinessRuleException("PACKAGING_LABEL_REQUIRED",
                    "Le nom de l'unite est obligatoire.");
        }
        refuseLabelTaken(label, product, packaging, false);
        BigDecimal factor = validFactor(request.factor(), product);
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
     */
    private static BigDecimal validFactor(BigDecimal factor, Product product) {
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
                    "Une unite qui contient exactement 1 " + baseUnitName(product)
                            + " est l'unite de base elle-meme.");
        }
        return factor.setScale(ProductPackaging.FACTOR_SCALE);
    }

    private static BigDecimal validPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessRuleException("PACKAGING_PRICE_INVALID",
                    "Le prix de l'unite doit etre superieur a zero.");
        }
        return price;
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

    /**
     * One code, one thing to sell -- across products and packagings alike, because the scanner
     * at the till looks in both and must never have to choose.
     */
    private void refuseBarcodeTaken(String barcode) {
        if (products.existsByBarcode(barcode) || packagings.existsByBarcode(barcode)) {
            throw new BusinessRuleException("BARCODE_TAKEN",
                    "Le code-barres " + barcode + " est deja utilise par un autre article.");
        }
    }

    private ProductUnitsResponse responseFor(Product product) {
        return ProductUnitsResponse.of(product,
                packagings.findByProductIdOrderByFactorAsc(product.getId()));
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Produit", id));
    }

    /** Addressed through its product, so a stale screen cannot edit another product's unit. */
    private ProductPackaging requirePackaging(Long productId, Long packagingId) {
        return packagings.findById(packagingId)
                .filter(packaging -> packaging.getProduct().getId().equals(productId))
                .orElseThrow(() -> ResourceNotFoundException.of("Unite", packagingId));
    }

    private static String baseUnitName(Product product) {
        return product.getUnit() == null ? "unite" : product.getUnit();
    }

    /** Trimmed and squeezed, null when nothing is left; the operator's capitals are kept. */
    private static String squeeze(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
