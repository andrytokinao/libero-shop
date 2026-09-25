package com.houssen.libertyshop.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * Reads the renewal code a customer sends in.
 *
 * <p>This is a deliberate copy of the format implemented by
 * {@code com.houssen.libertyshop.license.RenewalCode} in the backend, whose javadoc is the
 * authoritative description. It is duplicated rather than shared because this module must
 * stay dependency-free and must never pull the backend in -- that separation is what keeps
 * the private key out of the customer's jar, and it is worth more than the forty lines saved
 * by a shared artifact. The two implementations are checked against each other by a
 * round-trip test on the backend side and by issuing against a real code before release.
 *
 * <p>The code is not signed and proves nothing. The checksum catches a mistyped group; the
 * authority in this scheme is the Ed25519 signature on the {@code .lic} that goes back out.
 * So this reader exists for one reason: to spare the publisher from re-typing a twenty
 * character fingerprint and from looking up the previous expiry date by hand.
 */
final class RenewalCodeReader {

    private static final String PREFIX = "LSR1";

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int PAYLOAD_BYTES = 23;
    private static final int CHECKSUM_BYTES = 5;
    private static final int TOTAL_BYTES = PAYLOAD_BYTES + CHECKSUM_BYTES;
    private static final int CHARACTERS = (TOTAL_BYTES * 8 + 4) / 5;

    /** Bytes holding the 100-bit fingerprint, and the characters it renders as. */
    private static final int FINGERPRINT_BYTES = 13;
    private static final int FINGERPRINT_CHARACTERS = 20;
    private static final String FINGERPRINT_PREFIX = "LS1";

    private static final LocalDate EPOCH_DAY = LocalDate.parse("2020-01-01");
    private static final int NO_DATE = 0xFFFF;
    private static final int FORMAT_VERSION = 1;

    private static final byte[] CHECKSUM_CONTEXT =
            "liberty-shop/renewal-code/v1".getBytes(StandardCharsets.UTF_8);

    private RenewalCodeReader() {
    }

    /**
     * What a renewal code says.
     *
     * @param fingerprint   the machine to sign for, formatted as the customer sees it
     * @param state         1 ACTIVE, 2 GRACE, 3 READ_ONLY, 4 TRIAL, 5 TRIAL_EXPIRED, 0 unknown
     * @param currentExpiry expiry of the installed license, or {@code null} when there is
     *                      none -- this is what {@code --starts-on} should be
     * @param requestedOn   the day the customer produced the code
     * @param nonce         distinguishes two codes produced on the same day
     */
    record Decoded(String fingerprint, int state, LocalDate currentExpiry, LocalDate requestedOn, int nonce) {

        boolean isFirstPurchase() {
            return currentExpiry == null;
        }

        String describeState() {
            return switch (state) {
                case 1 -> "ACTIVE";
                case 2 -> "GRACE (expired, still usable)";
                case 3 -> "READ_ONLY (expired, writes refused)";
                case 4 -> "TRIAL";
                case 5 -> "TRIAL_EXPIRED (writes refused)";
                default -> "unknown";
            };
        }
    }

    static Decoded decode(String code) {
        byte[] bytes = decodeBase32(code);
        // Checksum first, version second: any mistyped character can land on the version
        // byte, and a version complaint would send you hunting for a tool upgrade when the
        // customer simply dropped a character.
        if (!MessageDigest.isEqual(checksum(bytes), Arrays.copyOfRange(bytes, PAYLOAD_BYTES, TOTAL_BYTES))) {
            throw new IllegalArgumentException("this renewal code does not check out -- ask the customer to "
                    + "send it again, it was very likely mistyped");
        }
        if ((bytes[0] & 0xFF) != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported renewal code version " + (bytes[0] & 0xFF)
                    + " -- this customer runs a newer application than this tool");
        }
        int nonce = (bytes[19] & 0xFF) << 24 | (bytes[20] & 0xFF) << 16 | (bytes[21] & 0xFF) << 8 | (bytes[22] & 0xFF);
        return new Decoded(
                unpackFingerprint(Arrays.copyOfRange(bytes, 1, 1 + FINGERPRINT_BYTES)),
                bytes[14] & 0xFF,
                getDate(bytes, 15),
                getDate(bytes, 17),
                nonce);
    }

    private static LocalDate getDate(byte[] bytes, int offset) {
        int value = (bytes[offset] & 0xFF) << 8 | (bytes[offset + 1] & 0xFF);
        return value == NO_DATE ? null : EPOCH_DAY.plusDays(value);
    }

    private static byte[] checksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CHECKSUM_CONTEXT);
            digest.update(bytes, 0, PAYLOAD_BYTES);
            return Arrays.copyOf(digest.digest(), CHECKSUM_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    /** Renders the packed 100 bits back into the {@code LS1-XXXXX-...} form. */
    private static String unpackFingerprint(byte[] packed) {
        StringBuilder out = new StringBuilder(FINGERPRINT_CHARACTERS);
        int buffer = 0;
        int bits = 0;
        for (byte b : packed) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5 && out.length() < FINGERPRINT_CHARACTERS) {
                bits -= 5;
                out.append(ALPHABET[(buffer >>> bits) & 0x1F]);
            }
        }
        StringBuilder grouped = new StringBuilder(FINGERPRINT_PREFIX);
        for (int i = 0; i < out.length(); i++) {
            if (i % 5 == 0) {
                grouped.append('-');
            }
            grouped.append(out.charAt(i));
        }
        return grouped.toString();
    }

    private static byte[] decodeBase32(String code) {
        if (code == null) {
            throw new IllegalArgumentException("a renewal code is required");
        }
        String body = code.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (!body.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a " + PREFIX + " renewal code: " + code);
        }
        body = body.substring(PREFIX.length());
        if (body.length() != CHARACTERS) {
            throw new IllegalArgumentException("expected " + CHARACTERS + " characters after the " + PREFIX
                    + " prefix, got " + body.length() + " -- the code looks truncated or padded");
        }
        byte[] bytes = new byte[TOTAL_BYTES];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < body.length(); i++) {
            buffer = (buffer << 5) | symbol(body.charAt(i));
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                bytes[index++] = (byte) (buffer >>> bits);
            }
        }
        // The final character holds four real bits plus one pad bit, which the encoder
        // always writes as zero. Accepting a one there would give every code a second valid
        // spelling that decodes identically -- so a customer who mistyped the last character
        // into that twin would pass the checksum unnoticed.
        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException("this renewal code does not check out -- its last character is "
                    + "wrong. Ask the customer to read the final group back to you.");
        }
        return bytes;
    }

    private static int symbol(char c) {
        char normalised = normalise(c);
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == normalised) {
                return i;
            }
        }
        throw new IllegalArgumentException("'" + c + "' is not part of the renewal code alphabet");
    }

    /**
     * Crockford's own leniency, and the reason the alphabet omits these letters: they are
     * the ones people confuse with digits when retyping. Reading them as the digit they
     * were meant to be beats rejecting a code that is otherwise perfectly correct.
     */
    private static char normalise(char c) {
        return switch (c) {
            case 'O' -> '0';
            case 'I', 'L' -> '1';
            default -> c;
        };
    }
}
