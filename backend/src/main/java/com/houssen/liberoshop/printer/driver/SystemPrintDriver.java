package com.houssen.liberoshop.printer.driver;

import com.houssen.liberoshop.entity.PayloadKind;
import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.exception.PrintException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import java.awt.print.PrinterJob;
import java.util.EnumSet;
import java.util.Set;

/** Imprimantes A4 déjà installées dans l'OS : PDF rendu par PDFBox et envoyé au spooler du système. */
@Component
public class SystemPrintDriver implements PrinterDriver {

    @Override
    public PrinterType type() {
        return PrinterType.SYSTEM;
    }

    @Override
    public Set<PayloadKind> supportedKinds() {
        return EnumSet.of(PayloadKind.PDF);
    }

    @Override
    public void print(PrinterConfig cfg, PrintJob job) {
        PrintService service = SystemPrinters.find(cfg.getAddress())
                .orElseThrow(() -> new PrintException(
                        "Imprimante introuvable dans le système : " + cfg.getAddress(), null));
        try (PDDocument document = Loader.loadPDF(job.payload())) {
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(service);
            printerJob.setJobName(job.title());
            printerJob.setPageable(new PDFPageable(document));
            printerJob.print();
        } catch (Exception e) {
            throw new PrintException("Échec de l'impression sur « " + service.getName() + " »", e);
        }
    }

    @Override
    public boolean isReachable(PrinterConfig cfg) {
        return SystemPrinters.find(cfg.getAddress()).map(SystemPrinters::isAccepting).orElse(false);
    }
}
