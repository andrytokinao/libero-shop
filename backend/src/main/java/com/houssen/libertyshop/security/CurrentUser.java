package com.houssen.libertyshop.security;

import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.entity.UserApp;
import com.houssen.libertyshop.repository.UserAppRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated {@link UserApp} for the services.
 *
 * <p>Services take the actor from here instead of from a request parameter. That is the
 * whole point: a cash desk cannot post a sale under someone else's name, and a depot agent
 * cannot remit another agent's cash, because the identity never travels in the payload.
 */
@Component
public class CurrentUser {

    private final UserAppRepository users;

    public CurrentUser(UserAppRepository users) {
        this.users = users;
    }

    /** The authenticated user, re-read from the database so edits take effect at once. */
    @Transactional(readOnly = true)
    public UserApp require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails principal)) {
            throw new AuthenticationCredentialsNotFoundException("Aucun utilisateur authentifie.");
        }
        return users.findById(principal.getId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Le compte connecte n'existe plus."));
    }

    public boolean hasRole(RoleApp role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
