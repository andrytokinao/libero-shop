package com.houssen.liberoshop.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * The single string a customer sends to order or renew a license.
 *
 * <p>Ordering a license used to mean reading the machine fingerprint over the phone. That
 * works, but it tells the publisher only <em>which</em> machine is asking -- not what it
 * already has, nor when it asked. Everything else had to be looked up by hand, in
 * particular the previous expiry date that {@code issue --starts-on} needs so the customer
 * does not lose days they already paid for. Looking it up by hand is where the mistakes
 * happen: a forgotten {@code --starts-on} gives days away, a wrong one takes them.
 *
 * <p>So the code carries the context instead:
 *
 * <pre>
 * LSR1-8G4KM-2PQW9-XZ7TB-4NH0V-J6RC3-9YM2K-D8F1S-W4XQ7-2BN6H
 * </pre>
 *
 * <table border="1">
 *   <caption>Layout, {@value #PAYLOAD_BYTES} bytes before the checksum</caption>
 *   <tr><th>Offset</th><th>Size</th><th>Field</th></tr>
 *   <tr><td>0</td><td>1</td><td>format version</td></tr>
 *   <tr><td>1</td><td>13</td><td>machine fingerprint, 100 bits packed</td></tr>
 *   <tr><td>14</td><td>1</td><td>current state, see {@link #stateCode}</td></tr>
 *   <tr><td>15</td><td>2</td><td>current expiry, days since {@value #EPOCH} ({@code 0xFFFF} = none)</td></tr>
 *   <tr><td>17</td><td>2</td><td>day the code was produced, same encoding</td></tr>
 *   <tr><td>19</td><td>4</td><td>random, so two codes from the same day differ</td></tr>
 *   <tr><td>23</td><td>5</td><td>checksum over everything above</td></tr>
 * </table>
 *
 * <p>The date the code was produced comes from {@link ClockGuard}'s corrected date, so it
 * cannot be made to look fresher than it is by winding the clock forward and back.
 *
 * <p><strong>The checksum is not a signature and the code carries no authority.</strong>
 * It catches a transcription error -- a dropped group, two characters swapped -- and
 * nothing more. It deliberately uses no secret: verifying one would mean shipping a shared
 * key into {@code tools/license-generator}, and it would buy very little. A customer who
 * edits their code to claim a later expiry gains nothing, because the publisher issues what
 * was paid for, and {@code install} already refuses a license that does not expire later
 * than the installed one. The authority in this scheme is the Ed25519 signature on the
 * {@code .lic} that comes back, and it is the only place it needs to be.
 */
public final class RenewalCode {

    /** Version prefix, mirroring the fingerprint's: a future layout can coexist. */
    public static final String PREFIX = "LSR1";

    /** Same Crockford alphabet as the fingerprint, for the same reason: no 1/I, no 0/O. */
    private static final char[] ALPHABET = Crockford.ALPHABET;

    /** Bytes covered by the checksum. */
    static final int PAYLOAD_BYTES = 23;

    /** Truncated digest length. Enough to make a typo overwhelmingly likely to be caught. */
    static final int CHECKSUM_BYTES = 5;

    static final int TOTAL_BYTES = PAYLOAD_BYTES + CHECKSUM_BYTES;

    /** Characters needed to carry {@link #TOTAL_BYTES} bytes, five bits at a time. */
    private static final int CHARACTERS = (TOTAL_BYTES * 8 + 4) / 5;

    /**
     * Dates travel as an offset from this day in two bytes, which reaches the year 2199 and
     * keeps the code short. A full epoch day would cost two more characters for nothing.
     */
    static final String EPOCH = "2020-01-01";

    private static final LocalDate EPOCH_DAY = LocalDate.parse(EPOCH);

    /** Marks a missing expiry date: an installation that has never held a license. */
    private static final int NO_DATE = 0xFFFF;

    private static final int FORMAT_VERSION = 1;

    /** State could not be evaluated: no license, no trial, or a file too broken to read. */
    static final int STATE_UNKNOWN = 0;

    /**
     * Domain separation for the checksum, so it cannot be confused with another digest.
     *
     * <p>Keeps the product's former name on purpose: the publisher's {@code RenewalCodeReader}
     * derives the same checksum, and the two have to agree for a code a customer reads out
     * over the phone to be accepted. Changing it means changing both at the same moment, which
     * would reject every code already in flight for nothing.
     */
    private static final byte[] CHECKSUM_CONTEXT =
            "liberty-shop/renewal-code/v1".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    private RenewalCode() {
    }

    /**
     * Builds the code for a status.
     *
     * @param status the current evaluation, licensed or on its trial
     * @return the grouped, human-transcribable code
     */
    public static String of(LicenseStatus status) {
        // A trial has no real expiry to renew from: the publisher is selling a first
        // license, not extending one, and must not inherit a locally minted date.
        LocalDate currentExpiry = status.isTrial() ? null : status.license().expiresOn();
        return encode(status.fingerprint(), stateCode(status.state()), currentExpiry, status.evaluatedOn(), nonce());
    }

    /**
     * The code for an installation whose state could not be evaluated at all -- no license
     * and no trial, or a file too broken to read.
     *
     * <p>This is precisely when the customer most needs to order one, so the code has to be
     * obtainable. It carries no expiry, which is correct: there is nothing to renew from.
     */
    public static String ofUnlicensed(String fingerprint, LocalDate today) {
        return encode(fingerprint, STATE_UNKNOWN, null, today, nonce());
    }

    /** Assembles and encodes one code. Package-private so tests can pin every field. */
    static String encode(String fingerprint, int state, LocalDate currentExpiry, LocalDate requestedOn, int nonce) {
        byte[] bytes = new byte[TOTAL_BYTES];
        bytes[0] = (byte) FORMAT_VERSION;
        System.arraycopy(MachineFingerprint.pack(fingerprint), 0, bytes, 1, MachineFingerprint.PACKED_BYTES);
        bytes[14] = (byte) state;
        putDate(bytes, 15, currentExpiry);
        putDate(bytes, 17, requestedOn);
        bytes[19] = (byte) (nonce >>> 24);
        bytes[20] = (byte) (nonce >>> 16);
        bytes[21] = (byte) (nonce >>> 8);
        bytes[22] = (byte) nonce;
        System.arraycopy(checksum(bytes), 0, bytes, PAYLOAD_BYTES, CHECKSUM_BYTES);
        return PREFIX + '-' + group(encodeBase32(bytes));
    }

    /**
     * Reads a code back.
     *
     * <p>Lives here rather than only in the publisher's tool so that the format has exactly
     * one authoritative definition, and so the round trip can be tested. The tool carries
     * its own reader because it must stay dependency-free and must never pull the backend
     * in -- {@code RenewalCodeReader} there is the deliberate copy, and this javadoc is the
     * contract both sides implement.
     *
     * @throws IllegalArgumentException if the code is malformed, mistyped or of another version
     */
    public static Decoded decode(String code) {
        byte[] bytes = decodeBase32(code);
        // Checksum first, version second. Corrupting any character can land on the version
        // byte, and "unsupported version 32" would send the reader looking for a tool
        // upgrade when all that happened is a mistyped group.
        byte[] expected = checksum(bytes);
        byte[] actual = Arrays.copyOfRange(bytes, PAYLOAD_BYTES, TOTAL_BYTES);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException(
                    "this renewal code does not check out -- it was very likely mistyped");
        }
        if ((bytes[0] & 0xFF) != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported renewal code version " + (bytes[0] & 0xFF));
        }
        byte[] packedFingerprint = Arrays.copyOfRange(bytes, 1, 1 + MachineFingerprint.PACKED_BYTES);
        int nonce = (bytes[19] & 0xFF) << 24 | (bytes[20] & 0xFF) << 16 | (bytes[21] & 0xFF) << 8 | (bytes[22] & 0xFF);
        return new Decoded(
                MachineFingerprint.unpack(packedFingerprint),
                bytes[14] & 0xFF,
                getDate(bytes, 15),
                getDate(bytes, 17),
                nonce);
    }

    /**
     * What a code says.
     *
     * @param fingerprint   the machine to sign for, ready to pass to {@code issue}
     * @param state         see {@link #stateCode}
     * @param currentExpiry expiry of the installed license, or {@code null} on a trial or a
     *                      machine that never held one -- this is the {@code --starts-on}
     * @param requestedOn   the day the customer produced the code
     * @param nonce         distinguishes two codes produced on the same day
     */
    public record Decoded(String fingerprint, int state, LocalDate currentExpiry, LocalDate requestedOn, int nonce) {

        /** Whether this machine has no license yet, so there is nothing to renew from. */
        public boolean isFirstPurchase() {
            return currentExpiry == null;
        }
    }

    /**
     * Wire value for a state. Explicit rather than {@link Enum#ordinal()}: reordering the
     * enum must not silently change the meaning of codes already in someone's inbox.
     */
    static int stateCode(LicenseState state) {
        if (state == null) {
            return STATE_UNKNOWN;
        }
        return switch (state) {
            case ACTIVE -> 1;
            case GRACE -> 2;
            case READ_ONLY -> 3;
            case TRIAL -> 4;
            case TRIAL_EXPIRED -> 5;
        };
    }

    // ------------------------------------------------------------------
    // Encoding
    // ------------------------------------------------------------------

    private static int nonce() {
        return RANDOM.nextInt();
    }

    private static void putDate(byte[] bytes, int offset, LocalDate date) {
        int value = NO_DATE;
        if (date != null) {
            long offsetDays = date.toEpochDay() - EPOCH_DAY.toEpochDay();
            if (offsetDays < 0 || offsetDays >= NO_DATE) {
                throw new IllegalArgumentException(date + " is outside the range a renewal code can carry ("
                        + EPOCH + " to " + EPOCH_DAY.plusDays(NO_DATE - 1L) + ")");
            }
            value = (int) offsetDays;
        }
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
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

    private static String encodeBase32(byte[] bytes) {
        StringBuilder out = new StringBuilder(CHARACTERS);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET[(buffer >>> bits) & 0x1F]);
            }
        }
        if (bits > 0) {
            // The last group is short: pad it with zero bits rather than drop it.
            out.append(ALPHABET[(buffer << (5 - bits)) & 0x1F]);
        }
        return out.toString();
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
            throw new IllegalArgumentException("expected " + CHARACTERS + " characters after the prefix, got "
                    + body.length() + " -- the code looks truncated or padded");
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
        // The last character carries four real bits and one pad bit. Left free, that pad bit
        // would give every code two valid spellings -- and a customer who mistyped the final
        // character into the other one would sail straight past the checksum, since the
        // decoded bytes are identical. Requiring it to be zero makes the encoding canonical.
        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException(
                    "this renewal code does not check out -- its last character is wrong");
        }
        return bytes;
    }

    private static int symbol(char c) {
        return Crockford.symbol(c);
    }

    /** Groups of five, like the fingerprint: readable, and a dropped group is obvious. */
    private static String group(String raw) {
        return Crockford.group(raw);
    }
}
