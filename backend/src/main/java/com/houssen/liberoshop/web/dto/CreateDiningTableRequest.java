package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Table 4", "Terrasse 2", "Bar": the name of a new table, or a table's new name. */
public record CreateDiningTableRequest(@NotBlank @Size(max = 40) String name) {
}
