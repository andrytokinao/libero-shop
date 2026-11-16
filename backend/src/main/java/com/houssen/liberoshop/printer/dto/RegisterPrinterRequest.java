package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.PrinterType;
import jakarta.validation.constraints.*;

public record RegisterPrinterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull PrinterType type,
        @NotBlank @Size(max = 255) String address,
        @Min(1) @Max(65535) Integer port,
        @Min(16) @Max(80) Integer widthChars) {
}
