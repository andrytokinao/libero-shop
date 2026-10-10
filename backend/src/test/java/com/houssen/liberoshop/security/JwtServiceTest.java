package com.houssen.liberoshop.security;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The token is the whole of the authentication now, so what matters is not that a valid
 * one is accepted -- a broken signature would fail every test in the suite -- but that the
 * three ways of presenting an invalid one are refused.
 *
 * <p>What a good token is worth -- which roles, whether its session is still open -- is read
 * from the account, not from the token: that is {@code AccessRightsTest}'s subject.
 */
class JwtServiceTest {

    private static final String SECRET = "cle-de-signature-de-test-assez-longue-pour-hs256";
    private static final String OTHER_SECRET = "une-tout-autre-cle-de-signature-aussi-longue";
    private static final String ISSUER = "libero-shop";

    private final JwtConfiguration configuration = new JwtConfiguration();

    /** Stands for the account lookup: these tests are about the signature, not the account. */
    private static final Converter<Jwt, AbstractAuthenticationToken> ANY_ACCOUNT = JwtAuthenticationToken::new;

    @Test
    @DisplayName("carries the account id and its session number, and reads them back")
    void roundTripsTheActor() {
        JwtService service = serviceWith(SECRET, ISSUER);

        UserApp fatima = accountOf(42L, "fatima", RoleApp.CASHIER);
        // Her password was changed twice: her tokens are of the third generation.
        fatima.revokeSessions();
        fatima.revokeSessions();
        String token = service.issue(new AppUserDetails(fatima)).value();
        Authentication authentication = service.authenticationOf(token);

        Jwt decoded = (Jwt) authentication.getPrincipal();
        assertEquals("fatima", decoded.getSubject());
        // A Number, not a Long: what JSON round-trips is a number of unspecified width,
        // which is exactly why the id is read through Number.longValue().
        assertEquals(42L, JwtService.userIdOf(decoded));
        assertEquals(2, JwtService.sessionVersionOf(decoded));
        // Named, so that one device can be signed out without the others.
        assertTrue(decoded.getId() != null && !decoded.getId().isBlank());
    }

    @Test
    @DisplayName("carries no role: what an account may do is read from the account")
    void carriesNoRole() {
        JwtService service = serviceWith(SECRET, ISSUER);

        String token = service.issue(detailsOf(7L, "soa", RoleApp.CASHIER, RoleApp.DEPOT_AGENT)).value();
        Jwt decoded = (Jwt) service.authenticationOf(token).getPrincipal();

        assertTrue(decoded.getClaims().keySet().stream().noneMatch(claim -> claim.contains("auth")),
                "a role written in the token would outlive its removal from the account");
    }

    @Test
    @DisplayName("reports a session closed since as a token that opens nothing")
    void reportsAClosedSessionAsABadToken() {
        JwtProperties properties = propertiesWith(SECRET, ISSUER);
        SecretKey key = configuration.jwtSigningKey(properties);
        JwtService service = new JwtService(configuration.jwtEncoder(key), configuration.jwtDecoder(key, properties),
                token -> {
                    throw new InvalidBearerTokenException("Cette session a ete fermee.");
                }, properties);

        String token = service.issue(detailsOf(42L, "fatima", RoleApp.CASHIER)).value();
        // The one exception the WebSocket handshake catches, whatever closed the session.
        assertThrows(JwtException.class, () -> service.authenticationOf(token));
    }

    @Test
    @DisplayName("refuses a token signed with another secret")
    void refusesAForeignSignature() {
        String forged = serviceWith(OTHER_SECRET, ISSUER)
                .issue(detailsOf(1L, "mparany", RoleApp.SUPER_ADMIN)).value();

        JwtService service = serviceWith(SECRET, ISSUER);
        assertThrows(JwtException.class, () -> service.authenticationOf(forged));
    }

    @Test
    @DisplayName("refuses a token issued by another application")
    void refusesAForeignIssuer() {
        // Same secret, different issuer: the signature is valid and the token is still
        // rejected, which is the point of checking iss at all.
        String foreign = serviceWith(SECRET, "une-autre-appli")
                .issue(detailsOf(1L, "mparany", RoleApp.SUPER_ADMIN)).value();

        JwtService service = serviceWith(SECRET, ISSUER);
        assertThrows(JwtException.class, () -> service.authenticationOf(foreign));
    }

    @Test
    @DisplayName("refuses a token whose expiry has passed")
    void refusesAnExpiredToken() {
        JwtProperties properties = propertiesWith(SECRET, ISSUER);
        SecretKey key = configuration.jwtSigningKey(properties);
        JwtEncoder encoder = configuration.jwtEncoder(key);

        // Built by hand rather than by shortening the ttl: the timestamp validator allows
        // a minute of clock skew, so an expiry has to be well in the past to mean anything.
        Instant expiry = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(expiry.minus(Duration.ofHours(12)))
                .expiresAt(expiry)
                .subject("fatima")
                .claim(JwtService.CLAIM_USER_ID, 42L)
                .build();
        String expired = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        JwtService service = new JwtService(encoder, configuration.jwtDecoder(key, properties),
                ANY_ACCOUNT, properties);
        assertThrows(JwtException.class, () -> service.authenticationOf(expired));
    }

    @Test
    @DisplayName("generates a key of its own when no secret is configured")
    void generatesAKeyWhenUnconfigured() {
        JwtProperties unconfigured = new JwtProperties("", Duration.ofHours(12), ISSUER);
        assertTrue(unconfigured.hasGeneratedKey());

        // Two start-ups, two keys: the warning logged for this case is not decoration.
        SecretKey first = configuration.jwtSigningKey(unconfigured);
        SecretKey second = configuration.jwtSigningKey(unconfigured);
        assertNotEquals(List.of(first.getEncoded()), List.of(second.getEncoded()));
    }

    @Test
    @DisplayName("refuses at start-up a secret too short to sign HS256")
    void refusesAShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties("trop-court", Duration.ofHours(12), ISSUER));
    }

    @Test
    @DisplayName("refuses a lifetime that has already elapsed")
    void refusesANonPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(SECRET, Duration.ZERO, ISSUER));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties(SECRET, Duration.ofMinutes(-5), ISSUER));
    }

    private JwtService serviceWith(String secret, String issuer) {
        JwtProperties properties = propertiesWith(secret, issuer);
        SecretKey key = configuration.jwtSigningKey(properties);
        return new JwtService(configuration.jwtEncoder(key), configuration.jwtDecoder(key, properties),
                ANY_ACCOUNT, properties);
    }

    private static JwtProperties propertiesWith(String secret, String issuer) {
        return new JwtProperties(secret, Duration.ofHours(12), issuer);
    }

    private static AppUserDetails detailsOf(long id, String username, RoleApp... roles) {
        return new AppUserDetails(accountOf(id, username, roles));
    }

    private static UserApp accountOf(long id, String username, RoleApp... roles) {
        return UserApp.builder()
                .id(id)
                .fullName("Compte de test")
                .username(username)
                .password("{bcrypt}ignore")
                .roles(EnumSet.copyOf(List.of(roles)))
                .enabled(true)
                .build();
    }
}
