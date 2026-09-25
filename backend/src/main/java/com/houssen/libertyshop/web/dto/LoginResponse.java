package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.security.JwtService.IssuedToken;

/**
 * What a successful {@code POST /api/auth/login} returns: the token, and the session it
 * stands for.
 *
 * <p>Both in one payload so the client can route straight after signing in instead of
 * making a second call to find out which role it just obtained.
 *
 * @param accessToken signed JWT, to be sent back as {@code Authorization: Bearer ...}
 * @param tokenType   always {@code Bearer}; sent so the client does not hard-code the scheme
 * @param expiresIn   seconds of validity left -- a duration rather than a date, so a
 *                    browser whose clock is wrong still expires the token at the right time
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn,
                            SessionResponse session) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(IssuedToken token, SessionResponse session) {
        return new LoginResponse(token.value(), BEARER, token.expiresIn(), session);
    }
}
