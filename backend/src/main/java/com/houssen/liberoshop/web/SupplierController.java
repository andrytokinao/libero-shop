package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.CatalogService;
import com.houssen.liberoshop.service.SupplierImportService;
import com.houssen.liberoshop.web.dto.ImportPreviewRequest;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import com.houssen.liberoshop.web.dto.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The shop's wholesalers.
 *
 * <p>Read by everyone -- the depot books receipts against this list, and a product import names
 * one on the whole file -- and written by the roles that run the depot.
 *
 * <p>Writing is only possible through an import today. That is a deliberate stopping point and
 * not a permanent one: the list had no write path at all, which left an installation with the
 * starter dataset switched off unable to book a single receipt, and reading a file of contacts
 * fixes that for far less work than a form would. A per-supplier form belongs here next.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final CatalogService catalogService;
    private final SupplierImportService supplierImportService;

    public SupplierController(CatalogService catalogService,
                              SupplierImportService supplierImportService) {
        this.catalogService = catalogService;
        this.supplierImportService = supplierImportService;
    }

    /** Each one carrying how much has actually been received from them. */
    @GetMapping
    public List<SupplierResponse> suppliers() {
        return catalogService.findSuppliers();
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
