package com.houssen.liberoshop.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Mints the access token handed out at login and turns one back into an {@link Authentication}.
 *
 * <p>The token carries only what a request needs to identify its actor: the account id, and
 * the number that says which generation of the account's sessions it belongs to. Names and
 * roles are deliberately left out -- they are re-read from the database on each call (see
 * {@link AccountAuthenticationConverter}), so an administrator's edit takes effect at once
 * instead of waiting for the token to expire.
 */
@Service
public class JwtService {

    /** Database id of the account, so a request resolves its actor without a name lookup. */
    public static final String CLAIM_USER_ID = "uid";

    /**
     * The account's {@link com.houssen.liberoshop.entity.UserApp#sessionVersion()} when the
     * token was signed: once the account's has moved on, the token is refused.
     */
    public static final String CLAIM_SESSION_VERSION = "ver";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder,
                      Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter,
                      JwtProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.authenticationConverter = authenticationConverter;
        this.properties = properties;
    }

    public IssuedToken issue(AppUserDetails user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                // Unique per token, so a leaked one can be named in an audit log.
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_SESSION_VERSION, user.getSessionVersion())
                .build();

        String value = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(value, expiresAt, properties.ttl().toSeconds());
    }

    /**
     * The authentication a token stands for, as the bearer-token filter would build it.
     *
     * <p>Used by the login handler so the session it returns is produced by the very token
     * it hands out, rather than by a second, parallel code path that could disagree with it.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException if the token is not ours,
     *                                                             expired or tampered with, or
     *                                                             if its account or its session
     *                                                             has been closed since
     */
    public Authentication authenticationOf(String token) {
        Jwt decoded = decoder.decode(token);
        try {
            return authenticationConverter.convert(decoded);
        } catch (AuthenticationException refused) {
            // One exception type for "this token opens nothing", whatever the reason: the
            // callers outside the HTTP filter chain -- the WebSocket handshake -- catch that one.
            throw new BadJwtException(refused.getMessage(), refused);
        }
    }

    /**
     * The account a decoded token speaks for.
     *
     * <p>JSON has one number type, so the claim comes back as whatever width the parser
     * chose; reading it as a {@link Number} avoids a cast that depends on that choice.
     */
    public static long userIdOf(Jwt token) {
        if (token.getClaim(CLAIM_USER_ID) instanceof Number id) {
            return id.longValue();
        }
        throw new AuthenticationCredentialsNotFoundException(
                "Jeton sans identifiant de compte : reconnectez-vous.");
    }

    /**
     * The generation of sessions a token belongs to. Zero when the claim is absent: the tokens
     * signed before the claim existed belong to the generation every account started in.
     */
    public static int sessionVersionOf(Jwt token) {
        return token.getClaim(CLAIM_SESSION_VERSION) instanceof Number version ? version.intValue() : 0;
    }

    /**
     * @param value     the signed token, to be sent back as {@code Authorization: Bearer ...}
     * @param expiresAt absolute expiry, for logging and tests
     * @param expiresIn seconds until expiry -- what the client stores, since it does not
     *                  depend on the browser's clock being right
     */
    public record IssuedToken(String value, Instant expiresAt, long expiresIn) {
    }
}
