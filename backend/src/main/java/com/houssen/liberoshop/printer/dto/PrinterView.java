package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.*;

import java.util.List;
import java.util.Set;

/**
 * @param online   null = état non vérifié
 * @param codePage table ESC/POS réellement utilisée (celle de l'imprimante ou, à défaut, du profil)
 */
public record PrinterView(
        String id,
        String name,
        PrinterType type,
        String address,
        Integer port,
        int widthChars,
        boolean enabled,
        EscPosProfile profile,
        int codePage,
        Boolean online,
        List<PrinterUsage> usages,
        Set<PayloadKind> kinds) {

    public static PrinterView of(PrinterConfig c, Boolean online, List<PrinterUsage> usages, Set<PayloadKind> kinds) {
        return new PrinterView(c.getId(), c.getName(), c.getType(), c.getAddress(), c.getPort(),
                c.getWidthChars(), c.isEnabled(), c.getProfile(), c.effectiveCodePage().table(),
                online, usages, kinds);
    }
}
