package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "user_app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    /** Login handle. Unique, and what Spring Security authenticates against. */
    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt hash, never the clear password. No getter is exposed over HTTP. */
    @Column(nullable = false)
    private String password;

    /** Lets an account be revoked without deleting the sales history it is linked to. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Every job this account may do -- a set, not a single value, because a small grocery is
     * often one person: the owner sells at the desk, hands the goods over and counts the
     * stock. A depot large enough to split those duties simply gives each account one role,
     * and the separation of duties it relies on is unchanged.
     *
     * <p>Eager: the roles are needed every time the account is loaded at all -- to
     * authenticate, to build a session, to serve a user list -- so a lazy set would only buy
     * a detached-collection failure. Never empty: an account that may do nothing could not
     * even be sent to a landing page, so {@link jakarta.validation.constraints.NotEmpty}
     * refuses it at save time rather than letting it fail at the next login.
     */
    @NotEmpty
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_app_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<RoleApp> roles = EnumSet.noneOf(RoleApp.class);

    /**
     * When the profile photo last changed, in epoch milliseconds; null while the account has
     * none. The photo itself lives in {@link UserPhoto}, so listing accounts never loads it --
     * this number is all a screen needs to know whether to show it, and to build a URL that
     * changes when it does (so a cached old photo is never shown for the new one).
     */
    private Long photoVersion;

    public boolean hasRole(RoleApp role) {
        return roles.contains(role);
    }
}
