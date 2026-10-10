package com.houssen.liberoshop.security;

import com.houssen.liberoshop.entity.RevokedToken;
import com.houssen.liberoshop.repository.RevokedTokenRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import com.houssen.liberoshop.service.AccountAccessChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Signing one device out: the token it holds is refused from now on, the account's other
 * sessions stand.
 *
 * <p>The other way to end a session -- all of an account's at once, when its password changes --
 * is {@link com.houssen.liberoshop.entity.UserApp#revokeSessions()}; both are checked on every
 * request by {@link AccountAuthenticationConverter}.
 */
@Service
public class SessionRevocation {

    private final RevokedTokenRepository revoked;
    private final ApplicationEventPublisher events;

    public SessionRevocation(RevokedTokenRepository revoked, ApplicationEventPublisher events) {
        this.events = events;
        this.revoked = revoked;
    }

    /** Refuses this token from now on. A token without an id or an expiry cannot be named: ignored. */
    @Transactional
    public void revoke(Jwt token) {
        if (token.getId() == null || token.getExpiresAt() == null) {
            return;
        }
        // Swept here rather than by a scheduler: a sign-out is the only moment the table grows.
        revoked.deleteByExpiresAtBefore(Instant.now());
        revoked.save(new RevokedToken(token.getId(), token.getExpiresAt()));
        // The device's socket was opened with this token: every connection of the account is
        // closed, and those whose token still stands come back by themselves.
        events.publishEvent(new AccountAccessChangedEvent(JwtService.userIdOf(token)));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(Jwt token) {
        return token.getId() != null && revoked.existsById(token.getId());
    }
}
