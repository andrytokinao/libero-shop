package com.houssen.liberoshop.update;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;

/**
 * The publisher's Ed25519 public key for application updates, compiled into the jar.
 *
 * <p>The value lives in the {@code update-key.properties} resource, packaged inside the jar:
 * it is not a runtime configuration property, so nothing on an installation can point the check
 * at another key. The release workflow writes that file from the UPDATE_PUBLIC_KEY GitHub
 * variable before building; the matching private key exists only as the UPDATE_SIGNING_KEY secret.
 */
public final class EmbeddedUpdateKey {

    /** Ed25519: native to the JDK since 15, no security provider to install. */
    static final String ALGORITHM = "Ed25519";

    private static final String RESOURCE = "/update-key.properties";

    private static final PublicKey PUBLIC_KEY = load();

    private EmbeddedUpdateKey() {
    }

    /** The key every update signature is checked against. */
    public static PublicKey publicKey() {
        return PUBLIC_KEY;
    }

    private static PublicKey load() {
        String base64;
        try (InputStream in = EmbeddedUpdateKey.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE + " in the application.");
            }
            Properties props = new Properties();
            props.load(in);
            base64 = props.getProperty("public.key");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE + ": " + e, e);
        }
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("No 'public.key' value in " + RESOURCE + ".");
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            // A broken key is a build mistake: fail loudly rather than accept every update.
            // The cause is in the message so it shows in logs that only print getMessage().
            throw new IllegalStateException("Invalid Ed25519 update public key in " + RESOURCE + ": " + e, e);
        }
    }
}