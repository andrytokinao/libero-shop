package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.RoleApp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * What a super-admin may change on an existing account: the person's name and the jobs they
 * may do.
 *
 * <p>No {@code username} and no {@code password} on purpose. The login handle is fixed once
 * created -- see {@code UserAccountService} for why -- and a password is set through its own
 * endpoint, so a rename cannot silently reset one.
 *
 * <p>{@code roles} is the whole new set, not a change to apply: the screen shows every role
 * with a box ticked or not, and sending that set back is exactly what the user saw.
 */
public record UpdateUserRequest(@NotBlank @Size(max = 80) String fullName,
                                @NotEmpty Set<RoleApp> roles) {
}
