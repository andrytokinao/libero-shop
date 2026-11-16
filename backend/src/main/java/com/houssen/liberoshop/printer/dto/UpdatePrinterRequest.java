package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.EscPosProfile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Tous les champs sont optionnels : seuls ceux fournis sont modifiés. */
public record UpdatePrinterRequest(
        @Size(max = 120) String name,
        @Min(1) @Max(65535) Integer port,
        @Min(16) @Max(80) Integer widthChars,
        Boolean enabled,
        EscPosProfile profile,
        Integer codePage) {
}
