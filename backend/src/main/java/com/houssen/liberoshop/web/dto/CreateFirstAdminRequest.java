package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.InitialSetupService;
import com.houssen.liberoshop.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The first account of an installation, as its owner fills it in. Always a super-admin: the
 * role is not asked, since whoever has none yet can give themselves no other.
 *
 * @param username login handle, held to the same rule as every later account's -- see
 *                 {@link CreateUserRequest}
 * @param password longer than an ordinary account's minimum: this one opens everything
 */
public record CreateFirstAdminRequest(
        @NotBlank @Size(max = 80) String fullName,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,30}",
                message = "3 a 30 caracteres : lettres, chiffres, point, tiret ou souligne")
        String username,

        @NotBlank
        @Size(min = InitialSetupService.MIN_ADMIN_PASSWORD_LENGTH,
                max = UserAccountService.MAX_PASSWORD_LENGTH,
                message = "de {min} a {max} caracteres")
        String password) {
}
