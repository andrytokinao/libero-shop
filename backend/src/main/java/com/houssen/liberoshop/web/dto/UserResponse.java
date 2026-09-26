package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.service.RolePolicy;

import java.util.List;

/**
 * A user as the UI needs them. The password hash has no field here, so it cannot leak
 * through a controller that returns the entity by accident.
 *
 * @param roles every job the account may do, in order of precedence -- one entry for a
 *              depot that splits the duties, several for the grocery where one person does
 *              everything
 */
public record UserResponse(Long id, String fullName, String username, List<RoleApp> roles,
                           boolean enabled) {

    public static UserResponse of(UserApp user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(user.getId(), user.getFullName(), user.getUsername(),
                RolePolicy.ordered(user.getRoles()), user.isEnabled());
    }
}
