package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.entity.UserApp;

/**
 * A user as the UI needs them. The password hash has no field here, so it cannot leak
 * through a controller that returns the entity by accident.
 */
public record UserResponse(Long id, String fullName, String username, RoleApp role, boolean enabled) {

    public static UserResponse of(UserApp user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(user.getId(), user.getFullName(), user.getUsername(),
                user.getRole(), user.isEnabled());
    }
}
