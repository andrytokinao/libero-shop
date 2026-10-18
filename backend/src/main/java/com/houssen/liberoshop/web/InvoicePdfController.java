package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.InvoicePdfService;
import com.houssen.liberoshop.service.InvoicePdfService.InvoicePdf;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The invoice as a PDF: opened in the browser, from where it is printed or shared. */
@RestController
@RequestMapping("/api/invoices")
public class InvoicePdfController {

    private final InvoicePdfService pdfs;
    private final CurrentUser currentUser;

    public InvoicePdfController(InvoicePdfService pdfs, CurrentUser currentUser) {
        this.pdfs = pdfs;
        this.currentUser = currentUser;
    }

    /** 403 for an invoice outside what the caller may read, as for the invoice itself. */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoicePdf pdf = pdfs.pdfFor(id, currentUser.require());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.invoiceNumber() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.content());
    }
}
