package com.houssen.liberoshop.printer.driver;


import com.houssen.liberoshop.entity.PayloadKind;
import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.exception.PrintException;
import org.springframework.stereotype.Component;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import java.util.EnumSet;
import java.util.Set;

/**
 * Imprimante ticket USB vue par l'OS (pilote « Generic / Text Only » suffisant) :
 * les octets ESC/POS sont envoyés en RAW au spooler, sans pilote spécifique au fabricant.
 */
@Component
public class EscPosRawSystemDriver implements PrinterDriver {

    @Override
    public PrinterType type() {
        return PrinterType.ESCPOS_RAW_SYSTEM;
    }

    @Override
    public Set<PayloadKind> supportedKinds() {
        return EnumSet.of(PayloadKind.ESCPOS);
    }

    @Override
    public void print(PrinterConfig cfg, PrintJob job) {
        PrintService service = SystemPrinters.find(cfg.getAddress())
                .orElseThrow(() -> new PrintException(
                        "Imprimante introuvable dans le système : " + cfg.getAddress(), null));
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        if (!service.isDocFlavorSupported(flavor)) {
            throw new PrintException("« " + service.getName() + " » n'accepte pas l'envoi RAW", null);
        }
        try {
            service.createPrintJob().print(new SimpleDoc(job.payload(), flavor, null), null);
        } catch (javax.print.PrintException e) {
            throw new PrintException("Échec de l'envoi RAW à « " + service.getName() + " »", e);
        }
    }

    @Override
    public boolean isReachable(PrinterConfig cfg) {
        return SystemPrinters.find(cfg.getAddress()).map(SystemPrinters::isAccepting).orElse(false);
    }
}
