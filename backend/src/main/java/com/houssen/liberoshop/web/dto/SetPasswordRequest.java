package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A password handed to an account by the super-admin.
 *
 * <p>The current password is not asked for, because the person who needs a new one is
 * usually the one who has forgotten the old one. What stands in for it is the caller's own
 * role: only a super-admin reaches the endpoint, and the change is theirs to answer for.
 */
public record SetPasswordRequest(
        @NotBlank
        @Size(min = UserAccountService.MIN_PASSWORD_LENGTH,
                max = UserAccountService.MAX_PASSWORD_LENGTH,
                message = "de {min} a {max} caracteres")
        String password) {
}
