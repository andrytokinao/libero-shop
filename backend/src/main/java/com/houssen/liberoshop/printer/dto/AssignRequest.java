package com.houssen.liberoshop.printer.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRequest(@NotBlank String printerId) {
}
