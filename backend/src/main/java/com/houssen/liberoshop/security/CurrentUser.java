package com.houssen.liberoshop.security;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated {@link UserApp} for the services.
 *
 * <p>Services take the actor from here instead of from a request parameter. That is the
 * whole point: a cash desk cannot post a sale under someone else's name, and a depot agent
 * cannot remit another agent's cash, because the identity never travels in the payload.
 *
 * <p>The token carries the account id, never the account itself: the row is re-read here
 * on every call, so a service always works with the account as it is now. Whether the caller
 * may come in at all was decided before, by {@link AccountAuthenticationConverter}; the check
 * below is kept for the callers that do not come through it.
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
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            throw new AuthenticationCredentialsNotFoundException("Aucun utilisateur authentifie.");
        }
        UserApp user = users.findById(JwtService.userIdOf(token))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Le compte connecte n'existe plus."));
        if (!user.isEnabled()) {
            // The token outlives the account being disabled; this is where that ends.
            throw new AuthenticationCredentialsNotFoundException("Ce compte a ete desactive.");
        }
        return user;
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
