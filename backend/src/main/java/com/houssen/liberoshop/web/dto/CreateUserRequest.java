package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * A new account, as the super-admin fills it in.
 *
 * <p>The clear password is carried once, hashed on arrival and then unobtainable:
 * {@link UserResponse} has no field for it, so no controller can hand it back by accident.
 *
 * @param username login handle, lower-cased on arrival. Sign-in matches it exactly, so an
 *                 installation holding both "Fatima" and "fatima" would be a trap; the
 *                 pattern also keeps spaces out of something people type at a counter.
 * @param roles    every job the account may do -- at least one, since an account with none
 *                 would have no page to land on
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 80) String fullName,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,30}",
                message = "3 a 30 caracteres : lettres, chiffres, point, tiret ou souligne")
        String username,

        @NotBlank
        @Size(min = UserAccountService.MIN_PASSWORD_LENGTH,
                max = UserAccountService.MAX_PASSWORD_LENGTH,
                message = "de {min} a {max} caracteres")
        String password,

        @NotEmpty Set<RoleApp> roles) {
}
