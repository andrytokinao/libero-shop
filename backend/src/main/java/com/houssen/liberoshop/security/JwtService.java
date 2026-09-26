package com.houssen.liberoshop.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints the access token handed out at login and turns one back into an {@link Authentication}.
 *
 * <p>The token carries only what a request needs to identify its actor: the account id and
 * the role. Names and permissions are deliberately left out -- they are re-read from the
 * database on each call, so an administrator's edit takes effect at once instead of
 * waiting for the token to expire.
 */
@Service
public class JwtService {

    /** Database id of the account, so a request resolves its actor without a name lookup. */
    public static final String CLAIM_USER_ID = "uid";

    /** Already {@code ROLE_} prefixed, so Spring Security can take them as they are. */
    public static final String CLAIM_AUTHORITIES = "authorities";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter authenticationConverter;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder,
                      JwtAuthenticationConverter authenticationConverter, JwtProperties properties) {
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
                .claim(CLAIM_AUTHORITIES, authoritiesOf(user))
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
     *                                                             expired or tampered with
     */
    public Authentication authenticationOf(String token) {
        Jwt decoded = decoder.decode(token);
        return authenticationConverter.convert(decoded);
    }

    private static List<String> authoritiesOf(AppUserDetails user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
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
