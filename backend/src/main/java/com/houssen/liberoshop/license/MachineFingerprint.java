package com.houssen.liberoshop.license;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes a stable identifier for the machine the application runs on.
 *
 * <p>The identity is built from two facts that survive ordinary hardware maintenance:
 * <ul>
 *   <li>the machine name;</li>
 *   <li>the serial number of the system volume (Windows) or the installation id
 *       (Linux {@code /etc/machine-id}, macOS platform UUID).</li>
 * </ul>
 *
 * <p>MAC addresses are deliberately <em>not</em> used. They looked attractive but in a
 * shop they change constantly: a USB Ethernet dock, a replaced Wi-Fi card or a VPN
 * adapter all shift the "primary" interface and would lock the customer out of their
 * own till. Disk identity only changes on a re-image, which is exactly when a reissued
 * license is legitimate anyway.
 *
 * <p>The result is a SHA-256 digest truncated to 100 bits and rendered in Crockford
 * Base32 ({@code LS1-XXXXX-XXXXX-XXXXX-XXXXX}). The alphabet omits I, L, O and U so a
 * customer can read the fingerprint over the phone without ambiguity, and the raw
 * hostname and serial never leave the machine.
 */
public final class MachineFingerprint {

    /** Version prefix: lets a future algorithm change coexist with already-issued licenses. */
    public static final String PREFIX = "LS1";

    /** Crockford Base32: no I, L, O or U, so 1/I, 0/O and similar cannot be confused. */
    private static final char[] ALPHABET = Crockford.ALPHABET;

    /** 20 Base32 characters = 100 bits of the digest; far beyond any collision concern here. */
    private static final int CHARACTERS = 20;

    /** Volume serial as printed by {@code vol}, e.g. {@code 1A2B-3C4D}. Locale independent. */
    private static final Pattern VOLUME_SERIAL = Pattern.compile("([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})");

    /** Marker used when no hardware id could be read, so the value stays deterministic. */
    private static final String UNKNOWN_SERIAL = "no-volume-serial";

    /** External commands must never hang the startup path. */
    private static final int COMMAND_TIMEOUT_SECONDS = 5;

    private static volatile String cached;

    private MachineFingerprint() {
    }

    /**
     * Fingerprint of the current machine. Computed once and cached: the underlying facts
     * cannot change while the JVM runs, and the probes shell out to the OS.
     */
    public static String current() {
        String local = cached;
        if (local == null) {
            synchronized (MachineFingerprint.class) {
                local = cached;
                if (local == null) {
                    String hostName =readHostName();
                    local = compute(hostName, readHardwareId());
                    cached = local;
                }
            }
        }
        return local;
    }

    /**
     * Derives the fingerprint from its two inputs. Package-private so tests can verify
     * determinism and sensitivity without touching the host OS.
     */
    static String compute(String hostName, String hardwareId) {
        String normalised = PREFIX + '|'
                + normalise(hostName) + '|'
                + normalise(hardwareId);
        byte[] digest = sha256(normalised.getBytes(StandardCharsets.UTF_8));
        return PREFIX + '-' + group(encodeBase32(digest));
    }

    /** Lowercases and strips whitespace so trivial formatting differences do not matter. */
    private static String normalise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------
    // Host name
    // ------------------------------------------------------------------

    /**
     * Reads the machine name. Environment variables come first because
     * {@code InetAddress.getLocalHost()} resolves through DNS and can return a different
     * name depending on the network the shop is connected to -- or throw outright when
     * offline, which is the normal case here.
     */
    static String readHostName() {
        return firstNonBlank(
                System.getenv("COMPUTERNAME"),
                System.getenv("HOSTNAME"),
                readFileQuietly(Path.of("/proc/sys/kernel/hostname")),
                resolveHostNameViaInetAddress())
                .orElse("unknown-host");
    }

    private static String resolveHostNameViaInetAddress() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            // Offline or misconfigured /etc/hosts: fall through to the "unknown" marker.
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Hardware / installation id
    // ------------------------------------------------------------------

    /** Dispatches to the OS-specific probe, never throwing. */
    static String readHardwareId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String id;
        if (os.contains("win")) {
            id = readWindowsVolumeSerial();
        } else if (os.contains("mac") || os.contains("darwin")) {
            id = readMacPlatformUuid();
        } else {
            id = readLinuxMachineId();
        }
        return firstNonBlank(id).orElse(UNKNOWN_SERIAL);
    }

    /**
     * Windows: the serial number of the system volume.
     *
     * <p>{@code vol} is tried first because it exists on every Windows version and costs
     * a few milliseconds. Its output is localised, so the serial is extracted by pattern
     * rather than by parsing the sentence. {@code wmic} is intentionally not used -- it is
     * deprecated and already absent from recent Windows 11 builds; PowerShell/CIM is the
     * fallback instead.
     */
    private static String readWindowsVolumeSerial() {
        String systemDrive = Optional.ofNullable(System.getenv("SystemDrive")).orElse("C:");

        String volOutput = runCommand(List.of("cmd", "/c", "vol", systemDrive));
        Optional<String> fromVol = matchVolumeSerial(volOutput);
        if (fromVol.isPresent()) {
            return fromVol.get();
        }

        String cimOutput = runCommand(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-CimInstance -ClassName Win32_LogicalDisk -Filter \"DeviceID='" + systemDrive
                        + "'\").VolumeSerialNumber"));
        return cimOutput == null ? null : cimOutput.trim();
    }

    private static Optional<String> matchVolumeSerial(String output) {
        if (output == null) {
            return Optional.empty();
        }
        Matcher matcher = VOLUME_SERIAL.matcher(output);
        return matcher.find() ? Optional.of(matcher.group(1) + "-" + matcher.group(2)) : Optional.empty();
    }

    /**
     * Linux: {@code /etc/machine-id} is generated at install time and is stable across
     * reboots, kernel upgrades and network changes. {@code /var/lib/dbus/machine-id}
     * covers older distributions; the root filesystem UUID is the last resort.
     */
    private static String readLinuxMachineId() {
        return firstNonBlank(
                readFileQuietly(Path.of("/etc/machine-id")),
                readFileQuietly(Path.of("/var/lib/dbus/machine-id")),
                runCommand(List.of("findmnt", "-no", "UUID", "/")))
                .orElse(null);
    }

    /** macOS: the hardware platform UUID. Not a target platform, but cheap to support. */
    private static String readMacPlatformUuid() {
        String output = runCommand(List.of("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"));
        if (output == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"").matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ------------------------------------------------------------------
    // Low level helpers
    // ------------------------------------------------------------------

    /**
     * Runs a probe command, returning its output or {@code null} on any failure.
     *
     * <p>Fingerprinting must never break startup, so every failure mode -- missing binary,
     * non-zero exit, hang -- degrades to {@code null} and lets the caller try the next
     * source.
     *
     * <p>Package-private rather than private: {@code TrialRegistry} shells out to
     * {@code reg.exe} and needs exactly these guarantees, and one hardened runner is worth
     * more than two similar ones.
     */
    static String runCommand(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readFileQuietly(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Optional<String> firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return Optional.of(candidate.trim());
            }
        }
        return Optional.empty();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    /** Encodes the leading bits of the digest, five bits per character. */
    private static String encodeBase32(byte[] digest) {
        StringBuilder out = new StringBuilder(CHARACTERS);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : digest) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5 && out.length() < CHARACTERS) {
                bitsInBuffer -= 5;
                out.append(ALPHABET[(buffer >>> bitsInBuffer) & 0x1F]);
            }
            if (out.length() == CHARACTERS) {
                break;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Conversion to and from the raw bits
    // ------------------------------------------------------------------

    /** Number of bytes needed to hold {@link #CHARACTERS} five-bit groups. */
    static final int PACKED_BYTES = (CHARACTERS * 5 + 7) / 8;

    /**
     * The {@value #CHARACTERS} five-bit groups behind a formatted fingerprint, packed into
     * {@link #PACKED_BYTES} bytes with the spare low bits left at zero.
     *
     * <p>Exists so a renewal code can carry the fingerprint in binary instead of as twenty
     * characters of text. Re-transcribing those characters is the single most error-prone
     * step of the whole offline workflow, which is exactly what the code removes.
     *
     * @throws IllegalArgumentException if the value is not a fingerprint this build emits
     */
    static byte[] pack(String fingerprint) {
        String body = strip(fingerprint);
        byte[] packed = new byte[PACKED_BYTES];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < body.length(); i++) {
            buffer = (buffer << 5) | symbol(body.charAt(i));
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                packed[index++] = (byte) (buffer >>> bits);
            }
        }
        if (bits > 0) {
            packed[index] = (byte) (buffer << (8 - bits));
        }
        return packed;
    }

    /** Rebuilds the display form from {@link #pack}'s output. */
    static String unpack(byte[] packed) {
        if (packed == null || packed.length != PACKED_BYTES) {
            throw new IllegalArgumentException("a packed fingerprint is " + PACKED_BYTES + " bytes");
        }
        return PREFIX + '-' + group(encodeBase32(packed));
    }

    /** Removes the prefix and the group separators, checking the shape on the way. */
    private static String strip(String fingerprint) {
        if (fingerprint == null) {
            throw new IllegalArgumentException("fingerprint is required");
        }
        String body = fingerprint.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if (!body.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a " + PREFIX + " fingerprint: " + fingerprint);
        }
        body = body.substring(PREFIX.length());
        if (body.length() != CHARACTERS) {
            throw new IllegalArgumentException("expected " + CHARACTERS + " characters after the prefix, got "
                    + body.length() + ": " + fingerprint);
        }
        return body;
    }

    /**
     * The five-bit value of one Crockford character, tolerating the letters a customer
     * substitutes when reading a fingerprint out over the phone.
     */
    private static int symbol(char c) {
        return Crockford.symbol(c);
    }

    /** Splits into groups of five for readability: {@code 4KQ8T-9WZ2M-H7PXR-C3NVB}. */
    private static String group(String raw) {
        return Crockford.group(raw);
    }
}
