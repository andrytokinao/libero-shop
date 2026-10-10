package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.InitialSetupService;
import com.houssen.liberoshop.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A lost administrator password, replaced from the server's computer.
 *
 * @param code     the one written in the recovery file -- see {@code AdminRecoveryService}
 * @param username the super-admin account to reopen
 * @param password held to the first administrator's minimum: this account opens everything
 */
public record RecoverAdminRequest(
        @NotBlank @Size(max = 20) String code,

        @NotBlank @Size(max = 30) String username,

        @NotBlank
        @Size(min = InitialSetupService.MIN_ADMIN_PASSWORD_LENGTH,
                max = UserAccountService.MAX_PASSWORD_LENGTH,
                message = "de {min} a {max} caracteres")
        String password) {
}
