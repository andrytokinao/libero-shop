package com.houssen.libertyshop.license;

import com.houssen.libertyshop.license.exception.LicenseExpiredException;
import com.houssen.libertyshop.license.exception.LicenseInvalidException;
import com.houssen.libertyshop.license.exception.LicenseNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Single entry point for everything license-related at runtime.
 *
 * <p>Holds the current {@link LicenseStatus}, refreshes it, enforces read-only mode and
 * installs renewed licenses. The signed file is the only source of truth: nothing is read
 * from or written to the database, so there is no {@code is_active} column to flip.
 *
 * <p>The status is recomputed on a day boundary rather than cached forever, so an
 * installation that stays up for weeks still degrades on the right day without a restart.
 */
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final LicenseVerifier verifier;
    private final LicenseProperties properties;
    private final ClockGuard clockGuard;
    private final Clock clock;
    private final String fingerprint;

    /** Last evaluation; {@code null} until the first check or while the module is disabled. */
    private volatile LicenseStatus status;

    public LicenseService(LicenseVerifier verifier, LicenseProperties properties, Clock clock, String fingerprint) {
        this.verifier = verifier;
        this.properties = properties;
        this.clock = clock;
        this.fingerprint = fingerprint;
        this.clockGuard = new ClockGuard(properties.resolvedClockGuardPath(), verifier.trustAnchor(),
                fingerprint, properties.clockGuard().enabled());
    }

    /**
     * Second verification pass, inside the Spring context.
     *
     * <p>{@code LicenseStartupListener} has normally already rejected a tampered file
     * before any bean was created. Repeating the check here costs a couple of
     * milliseconds and covers the paths that bypass the listener -- integration tests,
     * embedded launches, a context restart via Actuator.
     */
    @PostConstruct
    void verifyOnStartup() {
        if (!properties.enabled()) {
            log.warn("License checking is DISABLED (libertyshop.license.enabled=false). "
                    + "This must never be the case on a customer installation.");
            return;
        }
        LicenseStatus current = refresh();
        log.info("License: customer '{}' ({}), plan {}, expires {}, state {}.",
                current.license().customerName(), current.license().customerId(),
                current.license().plan(), current.license().expiresOn(), current.state());
        if (current.state() != LicenseState.ACTIVE) {
            log.warn("{}", current.userMessage());
        }
    }

    /** Re-reads and re-verifies the license file from disk. */
    public LicenseStatus refresh() {
        LicenseStatus fresh = verifier.verify(readLicenseFile(), fingerprint, evaluationDate());
        this.status = fresh;
        return fresh;
    }

    /**
     * Current status, recomputed if the day has rolled over since the last evaluation.
     *
     * @throws LicenseNotFoundException when checking is enabled but no file is installed
     */
    public LicenseStatus getStatus() {
        LicenseStatus current = this.status;
        if (current == null || !current.evaluatedOn().equals(evaluationDate())) {
            return refresh();
        }
        return current;
    }

    /**
     * Status as an {@link Optional}, never throwing.
     *
     * <p>For callers that must not fail on a license problem: the HTTP status endpoint,
     * and the renewal job which has to keep trying precisely when the license is broken.
     */
    public Optional<LicenseStatus> findStatus() {
        try {
            return Optional.of(getStatus());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Whether new sales, invoices and stock movements may currently be recorded. */
    public boolean isWriteAllowed() {
        if (!properties.enabled()) {
            return true;
        }
        return getStatus().writesAllowed();
    }

    /**
     * Gate used by {@link LicenseEnforcementAdvisor}.
     *
     * @throws LicenseExpiredException once past expiry and grace
     */
    public void assertWriteAllowed() {
        if (!properties.enabled()) {
            return;
        }
        LicenseStatus current = getStatus();
        if (!current.writesAllowed()) {
            throw new LicenseExpiredException(current.userMessage(), current.license().expiresOn());
        }
    }

    /**
     * Validates a renewed license and, only if it is genuinely better, installs it.
     *
     * <p>Two guards matter here. The new file is verified <em>before</em> it touches disk,
     * so a corrupt or hostile response from the renewal endpoint can never destroy a
     * working license. And a license that expires no later than the current one is
     * rejected, so a replayed old response cannot roll the customer backwards.
     *
     * @param rawEnvelope the candidate {@code .lic} content
     * @return the status after installation
     */
    public synchronized LicenseStatus install(byte[] rawEnvelope) {
        LicenseStatus candidate = verifier.verify(rawEnvelope, fingerprint, evaluationDate());

        LicenseStatus current = findStatus().orElse(null);
        if (current != null && !candidate.license().expiresOn().isAfter(current.license().expiresOn())) {
            throw new LicenseInvalidException("La licence proposee (expiration "
                    + candidate.license().expiresOn() + ") n'est pas plus recente que la licence installee ("
                    + current.license().expiresOn() + "). Installation refusee.");
        }

        writeAtomically(properties.path(), rawEnvelope);
        this.status = candidate;
        log.info("License installed: id {}, expires {}.", candidate.license().licenseId(),
                candidate.license().expiresOn());
        return candidate;
    }

    /** Fingerprint of this machine, to be sent to the publisher when ordering a license. */
    public String machineFingerprint() {
        return fingerprint;
    }

    /** Whether license checking is active at all. */
    public boolean isEnabled() {
        return properties.enabled();
    }

    LicenseProperties properties() {
        return properties;
    }

    /** Today, corrected for a clock that has been wound back. */
    private LocalDate evaluationDate() {
        return clockGuard.effectiveDate(LocalDate.now(clock));
    }

    private byte[] readLicenseFile() {
        Path path = properties.path();
        if (!Files.isRegularFile(path)) {
            throw new LicenseNotFoundException(
                    "Aucun fichier de licence trouve a l'emplacement " + path.toAbsolutePath() + ". "
                            + "Merci de communiquer l'empreinte de cette machine a l'editeur pour obtenir "
                            + "une licence : " + fingerprint);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new LicenseInvalidException(
                    "Le fichier de licence " + path.toAbsolutePath() + " n'a pas pu etre lu.", e);
        }
    }

    /**
     * Writes through a temporary file then moves it into place, so a crash or a power cut
     * mid-write cannot leave a half-written license behind. The previous file is kept as
     * {@code .bak} for support.
     */
    private static void writeAtomically(Path target, byte[] content) {
        try {
            Path directory = target.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            if (Files.exists(target)) {
                Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some Windows network shares do not support atomic moves.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new LicenseInvalidException(
                    "Le nouveau fichier de licence n'a pas pu etre enregistre dans " + target.toAbsolutePath()
                            + ".", e);
        }
    }
}
