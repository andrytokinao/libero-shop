package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The signed-in person replacing their own password.
 *
 * @param currentPassword proof that they are the holder, not someone at a screen left open
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = UserAccountService.MAX_PASSWORD_LENGTH) String currentPassword,

        @NotBlank
        @Size(min = UserAccountService.MIN_PASSWORD_LENGTH,
                max = UserAccountService.MAX_PASSWORD_LENGTH,
                message = "de {min} a {max} caracteres")
        String newPassword) {
}
