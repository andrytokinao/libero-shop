package com.houssen.liberoshop.update;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads a {@code .lsu} update file: a plain ZIP holding a small signed {@code envelope.json} and
 * the {@code payload.bin} it vouches for (the new application jar).
 *
 * <pre>
 * envelope.json  {
 *   "format": "libero-shop-update",
 *   "version": 1,
 *   "algorithm": "Ed25519",
 *   "manifest": "&lt;base64url of the signed manifest bytes&gt;",
 *   "signature": "&lt;base64 detached signature over those bytes&gt;"
 * }
 * payload.bin    the new fat jar
 * </pre>
 *
 * <p>Same indirection as the licence envelope: {@code manifest} is an opaque Base64URL blob so the
 * signature covers the <em>exact</em> bytes, never a re-serialisation of them. Opening the file
 * does not trust it -- {@link UpdateVerifier} checks the signature and the payload digest.
 */
public final class UpdatePackage {

    static final String FORMAT = "libero-shop-update";
    static final int SUPPORTED_VERSION = 1;
    static final String ENVELOPE_ENTRY = "envelope.json";
    static final String PAYLOAD_ENTRY = "payload.bin";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Path file;
    private final String algorithm;
    private final byte[] manifestBytes;
    private final byte[] signature;

    private UpdatePackage(Path file, String algorithm, byte[] manifestBytes, byte[] signature) {
        this.file = file;
        this.algorithm = algorithm;
        this.manifestBytes = manifestBytes;
        this.signature = signature;
    }

    /** Opens and structurally parses the envelope. Does not verify the signature. */
    public static UpdatePackage open(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new UpdateInvalidException("Le fichier de mise a jour est introuvable.");
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            byte[] envelope = readEntry(zip, ENVELOPE_ENTRY);
            if (zip.getEntry(PAYLOAD_ENTRY) == null) {
                throw new UpdateInvalidException("Le fichier de mise a jour ne contient pas de programme (payload.bin).");
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(new String(envelope, StandardCharsets.UTF_8));
            } catch (JacksonException e) {
                throw new UpdateInvalidException("Le fichier de mise a jour n'est pas un fichier valide.");
            }
            if (!FORMAT.equals(text(root, "format"))) {
                throw new UpdateInvalidException("Ce fichier n'est pas une mise a jour Libero Shop.");
            }
            int version = root.path("version").asInt(0);
            if (version > SUPPORTED_VERSION) {
                throw new UpdateInvalidException("Ce fichier de mise a jour (version " + version
                        + ") requiert une version plus recente de l'application.");
            }
            String algorithm = text(root, "algorithm");
            byte[] manifest = decode(text(root, "manifest"), Base64.getUrlDecoder(), "manifest");
            byte[] signature = decode(text(root, "signature"), Base64.getDecoder(), "signature");
            return new UpdatePackage(file, algorithm, manifest, signature);
        } catch (ZipException e) {
            throw new UpdateInvalidException("Le fichier de mise a jour est corrompu (archive illisible).");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String algorithm() {
        return algorithm;
    }

    byte[] manifestBytes() {
        return manifestBytes;
    }

    byte[] signature() {
        return signature;
    }

    /** Lower-case hex SHA-256 of the payload entry, read streaming so a large jar never loads whole. */
    String payloadSha256() {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = zip.getInputStream(payloadEntry(zip))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 absent de la JVM.", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Streams the payload jar to {@code destination}, overwriting it. */
    void copyPayloadTo(Path destination) {
        try (ZipFile zip = new ZipFile(file.toFile());
             InputStream in = zip.getInputStream(payloadEntry(zip))) {
            Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ZipEntry payloadEntry(ZipFile zip) {
        ZipEntry entry = zip.getEntry(PAYLOAD_ENTRY);
        if (entry == null) {
            throw new UpdateInvalidException("Le fichier de mise a jour ne contient pas de programme (payload.bin).");
        }
        return entry;
    }

    private static byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new UpdateInvalidException("Le fichier de mise a jour est incomplet (" + name + " manquant).");
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").trim();
    }

    private static byte[] decode(String value, Base64.Decoder decoder, String field) {
        if (value.isEmpty()) {
            throw new UpdateInvalidException("Le fichier de mise a jour ne contient pas de champ '" + field + "'.");
        }
        try {
            return decoder.decode(value);
        } catch (IllegalArgumentException e) {
            throw new UpdateInvalidException("Le champ '" + field + "' du fichier de mise a jour est corrompu.");
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
