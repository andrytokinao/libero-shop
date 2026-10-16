package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A wholesaler added by hand: a name, and how to reach them if known. */
public record CreateSupplierRequest(@NotBlank @Size(max = 120) String name,
                                    @Size(max = 120) String contact) {
}
