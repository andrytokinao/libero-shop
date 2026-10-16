package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding one wholesaler by hand -- from the receipt form, when the delivery is from somebody new.
 *
 * <p>The same name, written differently ("Grossiste Analakely", "grossiste  analakély"), is the
 * same supplier: it is refused rather than added twice, so the purchase prices stay on one line.
 */
@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    /** The supplier as it was saved: what the receipt form picks at once. */
    public record CreatedSupplier(Long id, String name, String contact) {

        static CreatedSupplier of(Supplier supplier) {
            return new CreatedSupplier(supplier.getId(), supplier.getName(), supplier.getContact());
        }
    }

    @RequiresActiveLicense
    @Transactional
    public CreatedSupplier create(String name, String contact) {
        String cleanName = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        if (cleanName.isEmpty()) {
            throw new BusinessRuleException("SUPPLIER_NAME_REQUIRED", "Le nom du fournisseur est obligatoire.");
        }
        String folded = CsvTable.normalise(cleanName);
        suppliers.findAllByOrderByNameAsc().stream()
                .filter(existing -> CsvTable.normalise(existing.getName()).equals(folded))
                .findFirst()
                .ifPresent(existing -> {
                    throw new BusinessRuleException("SUPPLIER_EXISTS",
                            "Le fournisseur \"" + existing.getName() + "\" existe deja.");
                });
        String cleanContact = contact == null || contact.isBlank() ? null : contact.trim();
        Supplier saved = suppliers.save(Supplier.builder().name(cleanName).contact(cleanContact).build());
        return CreatedSupplier.of(saved);
    }
}
