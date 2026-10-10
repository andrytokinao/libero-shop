package com.houssen.liberoshop.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The signing key an installation generates for itself and keeps between restarts.
 *
 * <p>A shop has nobody to write a secret into a configuration file, and a key drawn again at
 * each start would sign every cashier out whenever the computer reboots. So the first start
 * draws one and writes it here; every later start reads it back. Deleting the file is the
 * supported way to sign everybody out: the next start draws another.
 *
 * <p>The file holds the key as text, so it can be read, copied to a new machine or checked
 * for being empty without a tool. It is as secret as a password to the whole application:
 * it sits beside the database, which whoever can read it could read as well.
 */
final class JwtSecretFile {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretFile.class);

    /** 48 random bytes, 64 characters once encoded: well above what HS256 asks for. */
    private static final int GENERATED_BYTES = 48;

    private JwtSecretFile() {
    }

    /**
     * The key kept in {@code file}, drawn and written first when there is none -- or none
     * usable: a file cut short by a full disk is replaced rather than trusted.
     *
     * @throws IOException when the file can be neither read nor written; the caller decides
     *                     what the application signs with then
     */
    static byte[] loadOrCreate(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            byte[] kept = Files.readString(file, StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
            if (kept.length >= JwtProperties.MIN_SECRET_BYTES) {
                return kept;
            }
            log.warn("Cle de signature illisible dans {} : une nouvelle cle est generee, "
                    + "les sessions ouvertes devront se reconnecter.", file.toAbsolutePath());
        }
        return create(file);
    }

    private static byte[] create(Path file) throws IOException {
        byte[] random = new byte[GENERATED_BYTES];
        new SecureRandom().nextBytes(random);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        // Written beside and moved into place, so a crash never leaves half a key behind.
        Path draft = Files.createTempFile(target.getParent(), "jwt-secret", ".tmp");
        try {
            restrictToOwner(draft);
            Files.writeString(draft, key, StandardCharsets.UTF_8);
            try {
                Files.move(draft, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(draft, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(draft);
        }
        log.info("Cle de signature des sessions generee et conservee dans {}.", target);
        return key.getBytes(StandardCharsets.UTF_8);
    }

    /** Where the file system knows of owners; Windows keeps the folder's rights, the user's own. */
    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Not a POSIX file system: nothing to tighten here.
        }
    }
}
