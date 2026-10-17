package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The shop's wholesalers, one at a time: added from the receipt form when a delivery comes from
 * somebody new, corrected, or no longer offered.
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
    public record CreatedSupplier(Long id, String name, String contact, boolean retired) {

        static CreatedSupplier of(Supplier supplier) {
            return new CreatedSupplier(supplier.getId(), supplier.getName(), supplier.getContact(),
                    supplier.isRetired());
        }
    }

    @RequiresActiveLicense
    @Transactional
    public CreatedSupplier create(String name, String contact) {
        String cleanName = cleanName(name);
        refuseTwin(cleanName, null);
        Supplier saved = suppliers.save(Supplier.builder().name(cleanName).contact(cleanContact(contact)).build());
        return CreatedSupplier.of(saved);
    }

    /** Its name or its phone corrected; the same name as another supplier is still refused. */
    @RequiresActiveLicense
    @Transactional
    public CreatedSupplier update(Long supplierId, String name, String contact) {
        Supplier supplier = load(supplierId);
        String cleanName = cleanName(name);
        refuseTwin(cleanName, supplierId);
        supplier.setName(cleanName);
        supplier.setContact(cleanContact(contact));
        return CreatedSupplier.of(supplier);
    }

    /**
     * "Ne plus proposer": left out of the receipts' list of suppliers, its past receipts and prices
     * kept. False puts it back.
     */
    @RequiresActiveLicense
    @Transactional
    public CreatedSupplier changeRetired(Long supplierId, boolean retired) {
        Supplier supplier = load(supplierId);
        supplier.setRetired(retired);
        return CreatedSupplier.of(supplier);
    }

    private Supplier load(Long supplierId) {
        return suppliers.findById(supplierId)
                .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", supplierId));
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            throw new BusinessRuleException("SUPPLIER_NAME_REQUIRED", "Le nom du fournisseur est obligatoire.");
        }
        return clean;
    }

    private static String cleanContact(String contact) {
        return contact == null || contact.isBlank() ? null : contact.trim();
    }

    /** The same name, however written, as another supplier than {@code self}. */
    private void refuseTwin(String name, Long self) {
        String folded = CsvTable.normalise(name);
        suppliers.findAllByOrderByNameAsc().stream()
                .filter(existing -> !Objects.equals(existing.getId(), self))
                .filter(existing -> CsvTable.normalise(existing.getName()).equals(folded))
                .findFirst()
                .ifPresent(existing -> {
                    throw new BusinessRuleException("SUPPLIER_EXISTS",
                            "Le fournisseur \"" + existing.getName() + "\" existe deja.");
                });
    }
}
