package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Defends against the simplest way to extend a license for free: turning the system
 * clock back.
 *
 * <p>It keeps a small side file next to the license recording the latest date the
 * application has ever seen. Evaluation then uses {@code max(today, lastSeen)}, so
 * winding the clock back buys nothing -- the license keeps ageing.
 *
 * <p>The record is authenticated with an HMAC keyed on the embedded public key and the
 * machine fingerprint, so it cannot be edited by hand and cannot be lifted from another
 * installation. Deleting it is still possible, but that only erases history; it can never
 * make a license look younger than its own expiry date.
 *
 * <p>Failures are never fatal. A read-only directory or a missing file degrades to "no
 * rollback detected" rather than blocking the till: this is a speed bump against casual
 * tampering, not the primary control. The signature is.
 */
class ClockGuard {

    private static final Logger log = LoggerFactory.getLogger(ClockGuard.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Domain separation, so the derived key cannot collide with any other use.
     *
     * <p>Keeps the product's former name on purpose, and must: it is an input to the key that
     * authenticates the state file. Renaming it would make every installed file fail its
     * integrity check, be ignored, and hand back the very clock rollback this class exists to
     * defeat. Invisible to customers -- nothing ever prints it.
     */
    private static final byte[] KEY_CONTEXT = "liberty-shop/clock-guard/v1".getBytes(StandardCharsets.UTF_8);

    private final Path stateFile;
    private final boolean enabled;
    private final byte[] key;

    /**
     * In-memory mirror of the stored date. {@code effectiveDate} runs on every guarded
     * write -- once per sale -- so the file is read once at startup and written only when
     * the day actually advances, rather than on every call.
     */
    private volatile LocalDate lastSeen;
    private volatile boolean loaded;

    /**
     * @param stateFile    where to keep the record, normally beside the license file
     * @param trustAnchor  encoded public key of the build, mixed into the HMAC key
     * @param fingerprint  machine fingerprint, mixed into the HMAC key
     * @param enabled      lets deployments switch the guard off if it ever gets in the way
     */
    ClockGuard(Path stateFile, byte[] trustAnchor, String fingerprint, boolean enabled) {
        this.stateFile = stateFile;
        this.enabled = enabled;
        this.key = deriveKey(trustAnchor, fingerprint);
    }

    /**
     * Returns the date the license should be evaluated against, and records it.
     *
     * @param today the current system date
     * @return {@code today}, or the last seen date when the clock has moved backwards
     */
    LocalDate effectiveDate(LocalDate today) {
        if (!enabled) {
            return today;
        }
        loadOnce();
        LocalDate known = lastSeen;
        if (known != null && known.isAfter(today)) {
            log.warn("System clock appears to have moved backwards: last seen {}, system date {}. "
                    + "Evaluating the license against {}.", known, today, known);
            return known;
        }
        if (known == null || today.isAfter(known)) {
            lastSeen = today;
            writeLastSeen(today);
        }
        return today;
    }

    private void loadOnce() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (!loaded) {
                lastSeen = readLastSeen();
                loaded = true;
            }
        }
    }

    private LocalDate readLastSeen() {
        try {
            if (!Files.exists(stateFile)) {
                return null;
            }
            String content = Files.readString(stateFile, StandardCharsets.US_ASCII).trim();
            int separator = content.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            String epochDay = content.substring(0, separator);
            String mac = content.substring(separator + 1);
            if (!MessageDigest.isEqual(mac.getBytes(StandardCharsets.US_ASCII),
                    authenticate(epochDay).getBytes(StandardCharsets.US_ASCII))) {
                // Hand-edited or copied from another machine: ignore it rather than trust it.
                log.warn("License clock state at {} failed its integrity check and was ignored.", stateFile);
                return null;
            }
            return LocalDate.ofEpochDay(Long.parseLong(epochDay));
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read the license clock state at {}: {}", stateFile, e.toString());
            return null;
        }
    }

    private void writeLastSeen(LocalDate date) {
        try {
            if (stateFile.getParent() != null) {
                Files.createDirectories(stateFile.getParent());
            }
            String epochDay = Long.toString(date.toEpochDay());
            Files.writeString(stateFile, epochDay + ':' + authenticate(epochDay), StandardCharsets.US_ASCII);
            hideOnWindows();
        } catch (IOException | RuntimeException e) {
            // Read-only install directory, for instance. Not a reason to stop selling.
            log.debug("Could not write the license clock state at {}: {}", stateFile, e.toString());
        }
    }

    private String authenticate(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every JRE", e);
        }
    }

    /**
     * Derives the HMAC key from the trust anchor and the machine fingerprint. The public
     * key is not secret, but binding to it plus the fingerprint means the state file is
     * only meaningful for this build on this machine -- it cannot be copied from another
     * installation to fake an earlier "last seen" date.
     */
    private static byte[] deriveKey(byte[] trustAnchor, String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            digest.update(trustAnchor);
            digest.update(fingerprint.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    /** Keeps the state file out of the way in the customer's file explorer. Best effort. */
    private void hideOnWindows() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                Files.setAttribute(stateFile, "dos:hidden", Boolean.TRUE);
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            // Not a supported attribute view: harmless.
        }
    }
}
