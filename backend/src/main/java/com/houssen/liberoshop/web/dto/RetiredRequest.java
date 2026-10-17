package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param retired true to take the product off sale, false to put it back */
public record RetiredRequest(@NotNull Boolean retired) {
}
