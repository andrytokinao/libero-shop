package com.houssen.liberoshop.security;

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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Everything needed to mint and to read an access token.
 *
 * <p>What a token is worth once its signature holds -- which account, with which roles, and
 * whether its session is still open -- is not decided here but by
 * {@link AccountAuthenticationConverter}, from the database.
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
        if (!properties.hasGeneratedKey()) {
            return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        }
        if (properties.keepsItsKeyInAFile()) {
            Path file = Path.of(properties.secretFile());
            try {
                return new SecretKeySpec(JwtSecretFile.loadOrCreate(file), HMAC_ALGORITHM);
            } catch (IOException | RuntimeException e) {
                // A shop that cannot write its key must still open: it signs with a key of the
                // day, as below, and says why.
                log.error("Cle de signature inutilisable dans {} ({}).", file.toAbsolutePath(), e.toString());
            }
        }
        // Loud on purpose: it works, and it silently signs everyone out on restart.
        log.warn("Aucun secret JWT conserve : une cle aleatoire est generee au demarrage. "
                + "Les sessions ouvertes seront invalidees a chaque redemarrage. "
                + "Renseignez liberoshop.security.jwt.secret-file sur une installation client.");
        byte[] generated = new byte[JwtProperties.MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(generated);
        return new SecretKeySpec(generated, HMAC_ALGORITHM);
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
}
