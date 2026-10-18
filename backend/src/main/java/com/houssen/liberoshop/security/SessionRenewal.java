package com.houssen.liberoshop.security;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.security.JwtService.IssuedToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A session prolonged while it is still good: a cashier serving all day is not signed out in the
 * middle of a sale because the morning's token reached its end.
 *
 * <p>Only a token the server still accepts can be renewed -- the filter has already refused one
 * that expired, was revoked, or whose account was disabled or changed its password. The new token
 * is issued from the account as it stands now, and the old one is closed at once, so a renewal
 * never leaves two valid tokens behind.
 */
@Service
public class SessionRenewal {

    private final JwtService jwt;
    private final UserAppRepository users;
    private final SessionRevocation revocation;

    public SessionRenewal(JwtService jwt, UserAppRepository users, SessionRevocation revocation) {
        this.jwt = jwt;
        this.users = users;
        this.revocation = revocation;
    }

    @Transactional
    public IssuedToken renew(Jwt current) {
        UserApp user = users.findById(JwtService.userIdOf(current))
                .filter(UserApp::isEnabled)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Ce compte ne peut plus se connecter."));
        IssuedToken renewed = jwt.issue(new AppUserDetails(user));
        revocation.revoke(current);
        return renewed;
    }
}
