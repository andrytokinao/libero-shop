package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CatalogService;
import com.houssen.liberoshop.service.SupplierImportService;
import com.houssen.liberoshop.service.SupplierService;
import com.houssen.liberoshop.web.dto.CreateSupplierRequest;
import com.houssen.liberoshop.web.dto.ImportPreviewRequest;
import com.houssen.liberoshop.web.dto.RetiredRequest;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import com.houssen.liberoshop.web.dto.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The shop's wholesalers.
 *
 * <p>Read and written by the roles that run the depot: the list is what receipts are booked
 * against and what a product import names, and it says how much was bought from whom -- which
 * is not the counter's business any more than the purchase prices are.
 *
 * <p>Written by an import, or one at a time from the receipt form ("+ Nouveau fournisseur"),
 * when a delivery comes from somebody new.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final CatalogService catalogService;
    private final SupplierImportService supplierImportService;
    private final SupplierService supplierService;

    public SupplierController(CatalogService catalogService,
                              SupplierImportService supplierImportService,
                              SupplierService supplierService) {
        this.catalogService = catalogService;
        this.supplierImportService = supplierImportService;
        this.supplierService = supplierService;
    }

    /** Each one carrying how much has actually been received from them. */
    @GetMapping
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public List<SupplierResponse> suppliers() {
        return catalogService.findSuppliers();
    }

    /** A new supplier has received nothing yet. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
        return SupplierResponse.created(supplierService.create(request.name(), request.contact()));
    }

    /** Its name or its phone corrected. */
    @PutMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SupplierResponse update(@PathVariable Long supplierId, @Valid @RequestBody CreateSupplierRequest request) {
        return SupplierResponse.created(supplierService.update(supplierId, request.name(), request.contact()));
    }

    /** "Ne plus proposer", or offered again. */
    @PutMapping("/{supplierId}/retired")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SupplierResponse changeRetired(@PathVariable Long supplierId, @Valid @RequestBody RetiredRequest request) {
        return SupplierResponse.created(supplierService.changeRetired(supplierId, request.retired()));
    }

    /** What the file says, and what it would add to the list. Nothing is written. */
    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SimpleImportPreviewResponse previewImport(@Valid @RequestBody ImportPreviewRequest request) {
        return supplierImportService.preview(request.content());
    }

    @PostMapping("/import/apply")
    @PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
    public SimpleImportResultResponse applyImport(@Valid @RequestBody SimpleImportRequest request) {
        return supplierImportService.apply(request);
    }
}
