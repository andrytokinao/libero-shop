package com.houssen.libertyshop.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publisher-only command line tool: generates the Ed25519 signing key pair and
 * issues signed license files for customers.
 *
 * <p><strong>This class must never be shipped to a customer.</strong> It lives in a
 * separate Maven module ({@code tools/license-generator}) precisely so that it cannot
 * be pulled into the backend jar by accident. Anyone holding the private key can mint
 * unlimited licenses.
 *
 * <p>Usage:
 * <pre>
 *   # 1. One time only: create the key pair. Back up the private key offline.
 *   java -jar license-generator.jar keygen --out-dir ./keys
 *
 *   # 2. For each customer, using the fingerprint they read from their installation:
 *   java -jar license-generator.jar issue \
 *        --private-key ./keys/license-private.key \
 *        --customer-id CUST-0042 \
 *        --customer-name "Supermarche Houssen Analakely" \
 *        --fingerprint LS1-4KQ8T-9WZ2M-H7PXR-C3NVB \
 *        --plan ANNUAL \
 *        --out ./out/CUST-0042.lic
 *
 *   # 3. Sanity check before sending the file:
 *   java -jar license-generator.jar inspect --file ./out/CUST-0042.lic --public-key ./keys/license-public.key
 * </pre>
 *
 * <p>The generated file is a JSON envelope: a Base64URL-encoded payload plus its
 * detached Ed25519 signature. The signature always covers the exact payload bytes, so
 * the client never has to re-serialize JSON to verify it -- which removes the whole
 * class of canonicalization bugs.
 */
public final class LicenseGeneratorCli {

    /** Signature algorithm. Ed25519 is part of the JDK since 15 and needs no provider. */
    private static final String ALGORITHM = "Ed25519";

    /** Envelope discriminator, so a wrong file type fails with a clear message. */
    private static final String FORMAT = "liberty-shop-license";

    /** Envelope schema version, bumped if the payload shape ever changes. */
    private static final int VERSION = 1;

    /** Default number of days the app stays fully usable after the expiry date. */
    private static final int DEFAULT_GRACE_DAYS = 15;

    private LicenseGeneratorCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
        }
        String command = args[0];
        Map<String, List<String>> options = parseOptions(args, 1);
        try {
            switch (command) {
                case "keygen" -> keygen(options);
                case "issue" -> issue(options);
                case "inspect" -> inspect(options);
                case "help", "--help", "-h" -> printUsage();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(2);
                }
            }
        } catch (CliException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("unexpected failure: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * Generates the publisher key pair. Run once, then keep {@code license-private.key}
     * offline (encrypted backup, never in git); paste the public key into the client.
     */
    private static void keygen(Map<String, List<String>> options) throws Exception {
        Path outDir = Path.of(optional(options, "out-dir").orElse("./keys"));
        Files.createDirectories(outDir);

        Path privatePath = outDir.resolve("license-private.key");
        Path publicPath = outDir.resolve("license-public.key");
        if (Files.exists(privatePath) && !flag(options, "force")) {
            throw new CliException(privatePath + " already exists. Refusing to overwrite an existing "
                    + "signing key (that would invalidate every license already issued). Use --force "
                    + "only if you really mean it.");
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        KeyPair keyPair = generator.generateKeyPair();

        String privateBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        Files.writeString(privatePath, privateBase64 + System.lineSeparator(), StandardCharsets.US_ASCII);
        Files.writeString(publicPath, publicBase64 + System.lineSeparator(), StandardCharsets.US_ASCII);
        restrictToOwner(privatePath);

        System.out.println("Key pair generated (" + ALGORITHM + ").");
        System.out.println("  private key : " + privatePath.toAbsolutePath() + "   <-- keep secret, back up offline");
        System.out.println("  public key  : " + publicPath.toAbsolutePath());
        System.out.println();
        System.out.println("Paste this into EmbeddedLicenseKey.PUBLIC_KEY_BASE64 in the client application:");
        System.out.println();
        System.out.println("    \"" + publicBase64 + "\"");
        System.out.println();
    }

    /** Signs a new license file for one customer. */
    private static void issue(Map<String, List<String>> options) throws Exception {
        PrivateKey privateKey = loadPrivateKey(Path.of(required(options, "private-key")));

        String customerId = required(options, "customer-id");
        String customerName = required(options, "customer-name");
        List<String> fingerprints = options.getOrDefault("fingerprint", List.of());
        if (fingerprints.isEmpty()) {
            throw new CliException("at least one --fingerprint is required. Ask the customer to read it from "
                    + "the application (startup log, or GET /api/license/fingerprint).");
        }
        String plan = optional(options, "plan").orElse("ANNUAL").toUpperCase();
        if (!plan.equals("MONTHLY") && !plan.equals("ANNUAL") && !plan.equals("TRIAL")) {
            throw new CliException("--plan must be MONTHLY, ANNUAL or TRIAL (got: " + plan + ")");
        }

        // A renewal starts where the previous license ended, so the customer never loses
        // the days they already paid for: pass --starts-on <previous expiry>.
        LocalDate startsOn = optional(options, "starts-on").map(LicenseGeneratorCli::parseDate).orElse(LocalDate.now());
        LocalDate expiresOn = optional(options, "expires-on")
                .map(LicenseGeneratorCli::parseDate)
                .orElseGet(() -> defaultExpiry(startsOn, plan, optional(options, "months").map(Integer::parseInt).orElse(null)));
        if (!expiresOn.isAfter(startsOn)) {
            throw new CliException("expiry date (" + expiresOn + ") must be after the start date (" + startsOn + ")");
        }

        int graceDays = optional(options, "grace-days").map(Integer::parseInt).orElse(DEFAULT_GRACE_DAYS);
        if (graceDays < 0) {
            throw new CliException("--grace-days cannot be negative");
        }
        String licenseId = optional(options, "license-id")
                .orElse(customerId + "-" + expiresOn.getYear() + "-" + System.currentTimeMillis() / 1000);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("licenseId", licenseId);
        payload.put("customerId", customerId);
        payload.put("customerName", customerName);
        payload.put("plan", plan);
        payload.put("issuedOn", startsOn.toString());
        payload.put("expiresOn", expiresOn.toString());
        payload.put("graceDays", graceDays);
        payload.put("machineFingerprints", fingerprints);
        optional(options, "notes").ifPresent(notes -> payload.put("notes", notes));

        byte[] payloadBytes = Json.writeObject(payload).getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(privateKey, payloadBytes);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("format", FORMAT);
        envelope.put("version", VERSION);
        envelope.put("algorithm", ALGORITHM);
        envelope.put("payload", Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes));
        envelope.put("signature", Base64.getEncoder().encodeToString(signature));

        String envelopeJson = Json.writeObject(envelope);
        Path out = Path.of(optional(options, "out").orElse(customerId + ".lic"));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, envelopeJson + System.lineSeparator(), StandardCharsets.UTF_8);

        System.out.println("License issued: " + out.toAbsolutePath());
        System.out.println("  licence id   : " + licenseId);
        System.out.println("  customer     : " + customerName + " (" + customerId + ")");
        System.out.println("  plan         : " + plan);
        System.out.println("  valid        : " + startsOn + " -> " + expiresOn + " (+" + graceDays + " grace days)");
        System.out.println("  read-only on : " + expiresOn.plusDays(graceDays + 1L));
        System.out.println("  machines     : " + String.join(", ", fingerprints));
    }

    /**
     * Prints a license file and, when a public key is supplied, verifies its signature.
     * Use it as a last check before emailing a file to a customer.
     */
    private static void inspect(Map<String, List<String>> options) throws Exception {
        Path file = Path.of(required(options, "file"));
        String envelope = Files.readString(file, StandardCharsets.UTF_8);

        String payloadBase64 = extractStringField(envelope, "payload");
        String signatureBase64 = extractStringField(envelope, "signature");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);

        System.out.println("File    : " + file.toAbsolutePath());
        System.out.println("Payload : " + new String(payloadBytes, StandardCharsets.UTF_8));

        Optional<Path> publicKeyPath = optional(options, "public-key").map(Path::of);
        if (publicKeyPath.isEmpty()) {
            System.out.println("Signature: not checked (pass --public-key to verify)");
            return;
        }
        PublicKey publicKey = loadPublicKey(publicKeyPath.get());
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(payloadBytes);
        boolean valid = verifier.verify(Base64.getDecoder().decode(signatureBase64));
        System.out.println("Signature: " + (valid ? "VALID" : "INVALID"));
        if (!valid) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Crypto helpers
    // ------------------------------------------------------------------

    private static byte[] sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(privateKey);
        signer.update(payload);
        return signer.sign();
    }

    private static PrivateKey loadPrivateKey(Path path) throws Exception {
        byte[] der = Base64.getDecoder().decode(readKeyMaterial(path));
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey loadPublicKey(Path path) throws Exception {
        byte[] der = Base64.getDecoder().decode(readKeyMaterial(path));
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
    }

    /** Reads a key file, tolerating both raw Base64 and PEM-style armoured content. */
    private static String readKeyMaterial(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new CliException("key file not found: " + path.toAbsolutePath());
        }
        StringBuilder base64 = new StringBuilder();
        for (String line : Files.readAllLines(path, StandardCharsets.US_ASCII)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("-----") || trimmed.startsWith("#")) {
                continue;
            }
            base64.append(trimmed);
        }
        if (base64.isEmpty()) {
            throw new CliException("key file is empty: " + path.toAbsolutePath());
        }
        return base64.toString();
    }

    /** Best effort chmod 600 on the private key; silently skipped on Windows. */
    private static void restrictToOwner(Path path) {
        try {
            java.util.Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, ownerOnly);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows / non-POSIX file system: nothing to do.
        }
    }

    // ------------------------------------------------------------------
    // Argument handling
    // ------------------------------------------------------------------

    /** Parses {@code --key value} pairs and {@code --flag} switches; repeated keys accumulate. */
    private static Map<String, List<String>> parseOptions(String[] args, int from) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new CliException("unexpected argument '" + arg + "' (options must start with --)");
            }
            String key = arg.substring(2);
            String value = "true";
            int equals = key.indexOf('=');
            if (equals >= 0) {
                value = key.substring(equals + 1);
                key = key.substring(0, equals);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            options.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return options;
    }

    private static String required(Map<String, List<String>> options, String key) {
        return optional(options, key).orElseThrow(() -> new CliException("missing required option --" + key));
    }

    private static Optional<String> optional(Map<String, List<String>> options, String key) {
        List<String> values = options.get(key);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(values.size() - 1));
    }

    private static boolean flag(Map<String, List<String>> options, String key) {
        return optional(options, key).map(Boolean::parseBoolean).orElse(false);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CliException("invalid date '" + value + "', expected yyyy-MM-dd");
        }
    }

    private static LocalDate defaultExpiry(LocalDate startsOn, String plan, Integer months) {
        if (months != null) {
            return startsOn.plusMonths(months);
        }
        return switch (plan) {
            case "MONTHLY" -> startsOn.plusMonths(1);
            case "TRIAL" -> startsOn.plusDays(30);
            default -> startsOn.plusYears(1);
        };
    }

    /**
     * Pulls a top-level string field out of the envelope. The envelope only ever holds
     * flat Base64/ASCII values written by this tool, so a targeted regex is enough and
     * keeps the tool dependency-free. The client side uses a real JSON parser.
     */
    private static String extractStringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new CliException("field '" + field + "' not found: this does not look like a " + FORMAT + " file");
        }
        return matcher.group(1);
    }

    private static void printUsage() {
        System.out.println("""
                Liberty Shop license generator (publisher use only -- never ship this tool).

                Commands:
                  keygen   --out-dir <dir> [--force]
                  issue    --private-key <file> --customer-id <id> --customer-name <name>
                           --fingerprint <LS1-...> [--fingerprint <LS1-...> ...]
                           [--plan ANNUAL|MONTHLY|TRIAL] [--months <n>]
                           [--starts-on yyyy-MM-dd] [--expires-on yyyy-MM-dd]
                           [--grace-days <n>] [--license-id <id>] [--notes <text>] [--out <file>]
                  inspect  --file <file> [--public-key <file>]

                Renewal tip: pass --starts-on <previous expiry date> so the customer keeps
                the days they already paid for.
                """);
    }

    /** Thrown for user-facing argument or file problems; reported without a stack trace. */
    private static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }

    /** Minimal JSON writer: enough for the flat structures this tool emits. */
    private static final class Json {

        static String writeObject(Map<String, Object> values) {
            StringBuilder out = new StringBuilder("{\n");
            int remaining = values.size();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                out.append("  ").append(quote(entry.getKey())).append(": ").append(write(entry.getValue()));
                out.append(--remaining > 0 ? ",\n" : "\n");
            }
            return out.append('}').toString();
        }

        private static String write(Object value) {
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
            if (value instanceof List<?> list) {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    out.append(i > 0 ? ", " : "").append(write(list.get(i)));
                }
                return out.append(']').toString();
            }
            return quote(String.valueOf(value));
        }

        private static String quote(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            return out.append('"').toString();
        }
    }
}
