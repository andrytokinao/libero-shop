package com.houssen.liberoshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A token signed out before its time: the one a device was holding when its user touched
 * "Déconnexion".
 *
 * <p>One row per sign-out, named by the token's own id ({@code jti}), so signing out at the
 * counter does not close the session the same account has open on a phone. Closing all of
 * them at once is {@link UserApp#revokeSessions()}.
 *
 * <p>Kept until the token would have expired anyway; after that the signature check refuses
 * it without any help, and the row is swept.
 */
@Entity
@Table(name = "revoked_token")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @Column(length = 64)
    private String tokenId;

    @Column(nullable = false)
    private Instant expiresAt;
}
