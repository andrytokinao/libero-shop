package com.houssen.libertyshop.security;

import com.houssen.libertyshop.entity.UserApp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a {@link UserApp} to Spring Security without leaking the entity into the
 * security layer's contracts.
 *
 * <p>Only the identifier is kept from the entity, not the managed instance: the principal
 * lives in the HTTP session for the whole shift, and holding a detached entity there would
 * serve stale names and roles after an administrator edits the account. Services that need
 * the row re-read it through {@link CurrentUser}.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(UserApp user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        // One authority per role the account holds, so hasRole('CASHIER') answers true for
        // the owner of a small grocery who is also the depot. "ROLE_" prefix is what
        // hasRole(...) and @PreAuthorize expect. Sorted, so the token a login mints does not
        // depend on the order Hibernate happened to read the rows in.
        this.authorities = user.getRoles().stream()
                .sorted()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return enabled;
    }
}
