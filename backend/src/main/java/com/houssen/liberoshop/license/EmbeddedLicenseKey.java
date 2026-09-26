package com.houssen.liberoshop.license;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The publisher's Ed25519 public key, compiled into the application.
 *
 * <p>Hard-coded on purpose. It is deliberately <em>not</em> a configuration property: if a
 * customer could point the application at their own public key, they could sign their own
 * licenses and the whole scheme would be decorative. Changing this value requires
 * rebuilding and redistributing the application, which is exactly the intent.
 *
 * <p>A public key is not a secret -- publishing it in the jar reveals nothing. Only the
 * matching private key, held by the publisher in {@code tools/license-generator}, can
 * produce a signature this key accepts.
 *
 * <p>To rotate: run {@code license-generator keygen}, paste the printed value below,
 * rebuild, then reissue every customer license with the new private key.
 */
public final class EmbeddedLicenseKey {

    /**
     * X.509 / SubjectPublicKeyInfo encoding of the Ed25519 public key, Base64.
     * Printed by {@code java -jar license-generator.jar keygen}.
     */
    private static final String PUBLIC_KEY_BASE64 =
            "MCowBQYDK2VwAyEABy137vg4q5GHy/ccp7AK+nJFPfUymzR17qmbnmL7CHI=";

    /** Ed25519: native to the JDK since 15, no security provider to install. */
    static final String ALGORITHM = "Ed25519";

    private static final PublicKey PUBLIC_KEY = decode(PUBLIC_KEY_BASE64);

    private EmbeddedLicenseKey() {
    }

    /** The key every license signature is checked against. */
    public static PublicKey publicKey() {
        return PUBLIC_KEY;
    }

    private static PublicKey decode(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            // A broken constant is a build mistake, not a runtime condition: fail loudly
            // at class-load time rather than silently accepting every license.
            throw new IllegalStateException(
                    "The embedded license public key is not a valid Ed25519 key. Run "
                            + "'license-generator keygen' and paste the printed value into "
                            + EmbeddedLicenseKey.class.getName() + ".PUBLIC_KEY_BASE64.", e);
        }
    }
}
