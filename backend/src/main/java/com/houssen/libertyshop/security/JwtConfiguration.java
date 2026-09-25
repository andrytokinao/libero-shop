package com.houssen.libertyshop.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Everything needed to mint and to read an access token.
 *
 * <p>Symmetric signing (HS256): the same application issues the tokens and verifies them,
 * so a key pair would only add a public key nobody reads. Moving to RS256 is a matter of
 * swapping the three beans below -- nothing outside this class knows which algorithm is in
 * use.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        if (properties.hasGeneratedKey()) {
            // Loud on purpose: it works, and it silently signs everyone out on restart.
            log.warn("Aucun secret JWT configure : une cle aleatoire est generee au demarrage. "
                    + "Les sessions ouvertes seront invalidees a chaque redemarrage. "
                    + "Renseignez libertyshop.security.jwt.secret sur une installation client.");
            byte[] generated = new byte[JwtProperties.MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(generated);
            return new SecretKeySpec(generated, HMAC_ALGORITHM);
        }
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                // Pinned: without it a token could name its own algorithm in the header.
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Replaces the default validator set, which only checks the timestamps.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    /**
     * Reads the roles straight off the {@code authorities} claim.
     *
     * <p>The claim already carries the {@code ROLE_} prefix, so the prefix the converter
     * would otherwise add is cleared: the string in the token is the string
     * {@code hasRole(...)} and {@code @PreAuthorize} compare against, with no translation
     * step in between to get wrong.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.CLAIM_AUTHORITIES);
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
