package com.houssen.liberoshop.printer.driver;

import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.exception.PrintException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Découvre automatiquement tous les {@link PrinterDriver} présents dans le contexte Spring. */
@Component
public class PrinterRegistry {

    private final Map<PrinterType, PrinterDriver> drivers = new EnumMap<>(PrinterType.class);

    public PrinterRegistry(List<PrinterDriver> available) {
        for (PrinterDriver driver : available) {
            if (drivers.putIfAbsent(driver.type(), driver) != null) {
                throw new IllegalStateException("Deux drivers pour le type " + driver.type());
            }
        }
    }

    public PrinterDriver get(PrinterType type) {
        PrinterDriver driver = drivers.get(type);
        if (driver == null) {
            throw new PrintException("Aucun driver pour le type " + type, null);
        }
        return driver;
    }
}
