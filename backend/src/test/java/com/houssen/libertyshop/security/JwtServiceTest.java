package com.houssen.libertyshop.security;

import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.entity.UserApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

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
 */
class JwtServiceTest {

    private static final String SECRET = "cle-de-signature-de-test-assez-longue-pour-hs256";
    private static final String OTHER_SECRET = "une-tout-autre-cle-de-signature-aussi-longue";
    private static final String ISSUER = "liberty-shop";

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    @DisplayName("carries the account id and the role, and reads them back")
    void roundTripsTheActor() {
        JwtService service = serviceWith(SECRET, ISSUER);

        String token = service.issue(detailsOf(42L, "fatima", RoleApp.CASHIER)).value();
        Authentication authentication = service.authenticationOf(token);

        Jwt decoded = (Jwt) authentication.getPrincipal();
        assertEquals("fatima", decoded.getSubject());
        // A Number, not a Long: what JSON round-trips is a number of unspecified width,
        // which is exactly why CurrentUser reads it through Number.longValue().
        assertEquals(42L, ((Number) decoded.getClaim(JwtService.CLAIM_USER_ID)).longValue());
        // Contains rather than equals: Spring Security adds FACTOR_BEARER of its own,
        // saying how the caller authenticated. AuthController drops it before the
        // session reaches the client -- see the filter there.
        assertTrue(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList().contains("ROLE_CASHIER"));
    }

    @Test
    @DisplayName("carries every role of an account that holds several")
    void carriesEveryRoleOfTheAccount() {
        JwtService service = serviceWith(SECRET, ISSUER);

        // The one-person grocery: she sells at the desk and hands the goods over herself.
        String token = service.issue(detailsOf(7L, "soa", RoleApp.CASHIER, RoleApp.DEPOT_AGENT)).value();
        List<String> authorities = service.authenticationOf(token).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.containsAll(List.of("ROLE_CASHIER", "ROLE_DEPOT_AGENT")));
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
                .claim(JwtService.CLAIM_AUTHORITIES, List.of("ROLE_CASHIER"))
                .build();
        String expired = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        JwtService service = new JwtService(encoder, configuration.jwtDecoder(key, properties),
                configuration.jwtAuthenticationConverter(), properties);
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
                configuration.jwtAuthenticationConverter(), properties);
    }

    private static JwtProperties propertiesWith(String secret, String issuer) {
        return new JwtProperties(secret, Duration.ofHours(12), issuer);
    }

    private static AppUserDetails detailsOf(long id, String username, RoleApp... roles) {
        return new AppUserDetails(UserApp.builder()
                .id(id)
                .fullName("Compte de test")
                .username(username)
                .password("{bcrypt}ignore")
                .roles(EnumSet.copyOf(List.of(roles)))
                .enabled(true)
                .build());
    }
}
