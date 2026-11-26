package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Matière utilisée par la transformation, pertes de fabrication comprises. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialUseDto(
        Integer lineIndex,        // nullable
        String quantity
) {}
