package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.UnitModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameUnitModelRequest(@NotBlank @Size(max = UnitModel.MAX_NAME_LENGTH) String name) {
}
