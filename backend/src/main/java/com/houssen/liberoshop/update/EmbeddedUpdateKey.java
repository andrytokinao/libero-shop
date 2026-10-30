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
 * The publisher's Ed25519 public key for <em>application updates</em>, compiled into the app.
 *
 * <p>The value is injected at build time (GitHub variable UPDATE_PUBLIC_KEY -> Maven property
 * update.public.key -> filtered resource inside the jar). It is not a runtime configuration
 * property: no setting on an installation can point the check at a different key.
 *
 * <p>A separate key from the licence one on purpose: signing an update means shipping code that
 * runs as the server. The matching private key lives only in the GitHub secret
 * UPDATE_SIGNING_KEY, never in the repo.
 */
public final class EmbeddedUpdateKey {

    /** Ed25519: native to the JDK since 15, no security provider to install. */
    static final String ALGORITHM = "Ed25519";

    private static final String RESOURCE = "/update-key.properties";

    private static final PublicKey PUBLIC_KEY = decode(loadBase64());

    private EmbeddedUpdateKey() {
    }

    /** The key every update signature is checked against. */
    public static PublicKey publicKey() {
        return PUBLIC_KEY;
    }

    private static String loadBase64() {
        try (InputStream in = EmbeddedUpdateKey.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE + " in the application jar.");
            }
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty("public.key");
            if (value == null || value.isBlank() || value.startsWith("@")) {
                throw new IllegalStateException(
                        "The update public key was not injected at build time. Build with "
                                + "-Dupdate.public.key=<Base64 X.509 Ed25519 public key>.");
            }
            return value.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    private static PublicKey decode(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            // A broken key is a build mistake, not a runtime condition: fail loudly at
            // class-load time rather than silently accepting every update.
            throw new IllegalStateException(
                    "The embedded update public key is not a valid Ed25519 key.", e);
        }
    }
}