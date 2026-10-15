package com.houssen.liberoshop.update;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the update module: the properties, and the verifier bound to the embedded public key.
 *
 * <p>The verifier is built with {@link EmbeddedUpdateKey#publicKey()} here, exactly as the licence
 * module does -- the key is a constructor argument, never configuration, so no property can point
 * the check at a different key.
 */
@Configuration
@EnableConfigurationProperties(UpdateProperties.class)
public class UpdateConfiguration {

    @Bean
    public UpdateVerifier updateVerifier() {
        return new UpdateVerifier(EmbeddedUpdateKey.publicKey());
    }
}
