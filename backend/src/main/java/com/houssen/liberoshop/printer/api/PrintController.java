package com.houssen.liberoshop.printer.api;

import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterUsage;
import com.houssen.liberoshop.printer.service.PrintService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    /** Imprime un PDF (facture, rapport) sur l'imprimante affectée à l'usage. */
    @PostMapping(value = "/api/print/{usage}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> printPdf(@PathVariable PrinterUsage usage,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(defaultValue = "Document") String title) throws IOException {
        printService.printFor(usage, PrintJob.pdf(title, file.getBytes()));
        return ResponseEntity.noContent().build();
    }
}
