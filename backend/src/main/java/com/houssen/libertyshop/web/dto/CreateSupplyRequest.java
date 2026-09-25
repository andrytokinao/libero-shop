package com.houssen.libertyshop.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Goods received at the depot. The receiving agent is the authenticated user. */
public record CreateSupplyRequest(@NotNull Long productId,
                                  @NotNull Long supplierId,
                                  @Positive int quantity) {
}
