package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.PayloadKind;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.entity.PrinterUsage;

import java.util.List;
import java.util.Set;

/** @param online null = état non vérifié */
public record PrinterView(
        String id,
        String name,
        PrinterType type,
        String address,
        Integer port,
        int widthChars,
        boolean enabled,
        Boolean online,
        List<PrinterUsage> usages,
        Set<PayloadKind> kinds) {

    public static PrinterView of(PrinterConfig c, Boolean online, List<PrinterUsage> usages, Set<PayloadKind> kinds) {
        return new PrinterView(c.getId(), c.getName(), c.getType(), c.getAddress(), c.getPort(),
                c.getWidthChars(), c.isEnabled(), online, usages, kinds);
    }
}
