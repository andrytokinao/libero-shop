import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Signs an application update into a {@code .lsu} file the app will accept, and generates the
 * update signing key pair. Zero dependencies: run it straight from source on a JDK 17+.
 *
 * <p>The private key is the publisher's secret and never belongs in the repository (see
 * {@code backend/.gitignore} and {@code backend/update-signing/}). The matching public key is
 * compiled into the app as {@code EmbeddedUpdateKey.PUBLIC_KEY_BASE64}; only a {@code .lsu} signed
 * with this private key passes {@code UpdateVerifier}.
 *
 * <pre>
 * # generate a key pair (prints the public key to paste into EmbeddedUpdateKey, writes the private key)
 * java UpdateSigner.java keygen update-private.key
 *
 * # sign a built fat jar into a .lsu
 * java UpdateSigner.java sign \
 *     --key   ../../backend/update-signing/update-private.key \
 *     --jar   ../../backend/target/liberoshop-0.0.1-SNAPSHOT.jar \
 *     --version 1.3.0 \
 *     --notes "Corrections diverses." \
 *     --out   libero-shop-1.3.0.lsu
 * </pre>
 *
 * <p>The {@code .lsu} is a ZIP of {@code envelope.json} (format, version, algorithm, the signed
 * manifest as an opaque Base64URL blob, and its detached signature) and {@code payload.bin} (the
 * jar). The manifest pins the payload by its SHA-256, so the signature covers the code too.
 */
public final class UpdateSigner {

    private static final String ALGORITHM = "Ed25519";
    private static final String FORMAT = "libero-shop-update";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "keygen" -> keygen(args.length > 1 ? args[1] : "update-private.key");
            case "sign" -> sign(args);
            default -> usage();
        }
    }

    private static void keygen(String out) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        Files.writeString(Path.of(out),
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + System.lineSeparator());
        System.out.println("Private key written to " + out + " (keep it secret, never commit it).");
        System.out.println();
        System.out.println("Paste this into EmbeddedUpdateKey.PUBLIC_KEY_BASE64 and rebuild:");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static void sign(String[] args) throws Exception {
        String keyPath = null;
        String jarPath = null;
        String version = null;
        String notes = null;
        String releasedOn = LocalDate.now().toString();
        String out = null;
        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--key" -> keyPath = args[++i];
                case "--jar" -> jarPath = args[++i];
                case "--version" -> version = args[++i];
                case "--notes" -> notes = args[++i];
                case "--released-on" -> releasedOn = args[++i];
                case "--out" -> out = args[++i];
                default -> { }
            }
        }
        if (keyPath == null || jarPath == null || version == null || out == null) {
            usage();
            return;
        }

        byte[] payload = Files.readAllBytes(Path.of(jarPath));
        String sha = sha256Hex(payload);
        String manifestJson = "{"
                + "\"appVersion\":\"" + version + "\","
                + "\"releasedOn\":\"" + releasedOn + "\","
                + "\"payloadSha256\":\"" + sha + "\""
                + (notes == null ? "" : ",\"notes\":" + jsonString(notes))
                + "}";
        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signBytes(readPrivateKey(keyPath), manifestBytes);

        String envelopeJson = "{"
                + "\"format\":\"" + FORMAT + "\","
                + "\"version\":1,"
                + "\"algorithm\":\"" + ALGORITHM + "\","
                + "\"manifest\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(manifestBytes) + "\","
                + "\"signature\":\"" + Base64.getEncoder().encodeToString(signature) + "\""
                + "}";

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(Path.of(out)))) {
            zip.putNextEntry(new ZipEntry("envelope.json"));
            zip.write(envelopeJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("payload.bin"));
            zip.write(payload);
            zip.closeEntry();
        }
        System.out.println("Wrote " + out + " (version " + version + ", payload " + payload.length + " bytes).");
    }

    private static PrivateKey readPrivateKey(String path) throws Exception {
        String base64 = Files.readString(Path.of(path)).trim();
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] signBytes(PrivateKey key, byte[] bytes) throws Exception {
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(bytes);
        return signer.sign();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void usage() {
        System.out.println("""
                UpdateSigner — sign a Moussa Shop application update.

                  java UpdateSigner.java keygen [private-key-out]
                  java UpdateSigner.java sign --key <private.key> --jar <fat.jar> \\
                       --version <x.y.z> [--notes "..."] [--released-on yyyy-MM-dd] --out <file.lsu>
                """);
    }

    private UpdateSigner() {
    }
}
