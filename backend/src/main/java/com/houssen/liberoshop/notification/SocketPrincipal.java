package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;

import java.security.Principal;
import java.util.Set;

/**
 * Who is at the other end of a WebSocket connection.
 *
 * <p>Named by the account's database id rather than its login: that is what the services hold
 * when they decide whom to tell, and it makes {@code convertAndSendToUser(id, ...)} the whole
 * addressing scheme. The roles are those of the token presented on connection, kept so a
 * message meant for a job can be routed without a query per send.
 */
public record SocketPrincipal(Long userId, Set<RoleApp> roles) implements Principal {

    public SocketPrincipal {
        roles = Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public boolean holdsAny(Set<RoleApp> wanted) {
        return roles.stream().anyMatch(wanted::contains);
    }
}
