package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.EscPosProfile;
import com.houssen.liberoshop.entity.PrinterType;
import jakarta.validation.constraints.*;

/**
 * @param profile    null = GENERIC ; XPRINTER_80 / XPRINTER_58 pour une XPrinter
 * @param widthChars null = largeur par défaut du profil
 * @param codePage   null = table du profil ; sinon 0, 2, 16 ou 19
 */
public record RegisterPrinterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull PrinterType type,
        @NotBlank @Size(max = 255) String address,
        @Min(1) @Max(65535) Integer port,
        @Min(16) @Max(80) Integer widthChars,
        EscPosProfile profile,
        Integer codePage) {
}
