package com.houssen.liberoshop.update;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The publisher's Ed25519 public key for <em>application updates</em>, compiled into the app.
 *
 * <p>A separate key from the licence one on purpose. Signing an update means shipping new code
 * that runs as the server; signing a licence only unlocks an install. Keeping the two private
 * keys apart means whoever can issue licences cannot, with that key alone, push code onto every
 * installation.
 *
 * <p>Hard-coded, not a configuration property: if a customer could point the app at their own
 * public key they could sign their own payload and the whole scheme would be decorative. Only the
 * matching private key, held by the publisher (see {@code update-signing/}, never in the repo),
 * can produce a signature this key accepts.
 *
 * <p>To rotate: generate a new Ed25519 pair, paste the printed public value below, rebuild, and
 * sign every future update with the new private key.
 */
public final class EmbeddedUpdateKey {

    /** X.509 / SubjectPublicKeyInfo encoding of the Ed25519 public key, Base64. */
    private static final String PUBLIC_KEY_BASE64 =
            "MCowBQYDK2VwAyEAwQ5fIx1NCS2Ba/othH7vwYWzVOv8fjfmmbfevh21agA=";

    /** Ed25519: native to the JDK since 15, no security provider to install. */
    static final String ALGORITHM = "Ed25519";

    private static final PublicKey PUBLIC_KEY = decode(PUBLIC_KEY_BASE64);

    private EmbeddedUpdateKey() {
    }

    /** The key every update signature is checked against. */
    public static PublicKey publicKey() {
        return PUBLIC_KEY;
    }

    private static PublicKey decode(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            // A broken constant is a build mistake, not a runtime condition: fail loudly at
            // class-load time rather than silently accepting every update.
            throw new IllegalStateException(
                    "The embedded update public key is not a valid Ed25519 key. Generate an Ed25519 "
                            + "key pair and paste the public value into "
                            + EmbeddedUpdateKey.class.getName() + ".PUBLIC_KEY_BASE64.", e);
        }
    }
}
