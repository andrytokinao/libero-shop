package com.houssen.liberoshop.security;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a token whose signature holds into the caller it speaks for -- by reading the account,
 * not the token.
 *
 * <p>The token says who: an account id. What that account may do, and whether it may still do
 * anything, is read from its row on every request. So a role taken away, an account switched
 * off, a password changed or a device signed out all bite on the very next call, instead of
 * when the token runs out twelve hours later.
 *
 * <p>That costs a read of the account per request, on a database that sits on the same
 * machine. It buys the one thing a signed token cannot give by itself: taking an access back.
 *
 * <p>Everything refused here is an {@link AuthenticationException}, so the caller gets the same
 * 401 as for an expired token and the application sends them to the sign-in page.
 */
@Component
public class AccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAppRepository users;
    private final SessionRevocation revocation;

    public AccountAuthenticationConverter(UserAppRepository users, SessionRevocation revocation) {
        this.users = users;
        this.revocation = revocation;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        UserApp user = users.findById(JwtService.userIdOf(token))
                .orElseThrow(() -> refused("Le compte connecte n'existe plus."));
        if (!user.isEnabled()) {
            throw refused("Ce compte a ete desactive.");
        }
        if (JwtService.sessionVersionOf(token) != user.sessionVersion()) {
            // Signed before the password changed: every session of the account ended there.
            throw refused("Cette session a ete fermee : reconnectez-vous.");
        }
        if (revocation.isRevoked(token)) {
            throw refused("Cette session a ete fermee : reconnectez-vous.");
        }
        return new JwtAuthenticationToken(token, AppUserDetails.authoritiesOf(user.getRoles()),
                token.getSubject());
    }

    private static AuthenticationException refused(String why) {
        return new InvalidBearerTokenException(why);
    }
}
