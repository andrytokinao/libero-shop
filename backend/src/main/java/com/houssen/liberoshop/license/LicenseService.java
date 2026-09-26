package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseExpiredException;
import com.houssen.liberoshop.license.exception.LicenseInvalidException;
import com.houssen.liberoshop.license.exception.LicenseNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Single entry point for everything license-related at runtime.
 *
 * <p>Holds the current {@link LicenseStatus}, refreshes it, enforces read-only mode and
 * installs renewed licenses. The signed file is the only source of truth: nothing is read
 * from or written to the database, so there is no {@code is_active} column to flip.
 *
 * <p>With no file installed, {@link TrialRegistry} supplies a one-off evaluation period
 * instead of blocking the boot. It can only ever grant what the machine has not already
 * consumed, so this is a gentler start for a new installation, not a way back out of an
 * expired one.
 *
 * <p>The status is recomputed on a day boundary rather than cached forever, so an
 * installation that stays up for weeks still degrades on the right day without a restart.
 */
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    /**
     * Name the {@code .lic} file was given while the product was called Liberty Shop.
     *
     * <p>Read as a fallback, never written. See {@link #installedLicensePath()}.
     */
    private static final String LEGACY_LICENSE_FILENAME = "liberty-shop.lic";

    private final LicenseVerifier verifier;
    private final LicenseProperties properties;
    private final ClockGuard clockGuard;
    private final TrialRegistry trialRegistry;
    private final BusinessDataWitness witness;
    private final Clock clock;
    private final String fingerprint;

    /** Last evaluation; {@code null} until the first check or while the module is disabled. */
    private volatile LicenseStatus status;

    /**
     * Without a database handle: the startup listener, which runs before any bean exists,
     * and the unit tests, which have no pool either.
     */
    public LicenseService(LicenseVerifier verifier, LicenseProperties properties, Clock clock, String fingerprint) {
        this(verifier, properties, clock, fingerprint, () -> null);
    }

    /**
     * @param dataSource lazy handle on the application database, where the trial registry
     *                   keeps the one copy of its record that cannot be erased with a
     *                   file manager. Resolved on first use, so declaring it here never
     *                   forces a connection pool to be built early.
     */
    public LicenseService(LicenseVerifier verifier, LicenseProperties properties, Clock clock, String fingerprint,
                          Supplier<DataSource> dataSource) {
        this.verifier = verifier;
        this.properties = properties;
        this.clock = clock;
        this.fingerprint = fingerprint;
        this.clockGuard = new ClockGuard(properties.resolvedClockGuardPath(), verifier.trustAnchor(),
                fingerprint, properties.clockGuard().enabled());
        this.witness = new BusinessDataWitness(dataSource);
        this.trialRegistry = new TrialRegistry(properties, verifier.trustAnchor(), fingerprint, dataSource, witness);
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
            log.warn("License checking is DISABLED (liberoshop.license.enabled=false). "
                    + "This must never be the case on a customer installation.");
            return;
        }
        LicenseStatus current = refresh();
        if (current.isTrial()) {
            log.info("No license installed: evaluation period from {} to {}, state {}.",
                    current.license().issuedOn(), current.license().expiresOn(), current.state());
        } else {
            log.info("License: customer '{}' ({}), plan {}, expires {}, state {}.",
                    current.license().customerName(), current.license().customerId(),
                    current.license().plan(), current.license().expiresOn(), current.state());
        }
        if (current.state() != LicenseState.ACTIVE) {
            log.warn("{}", current.userMessage());
        }
    }

    /**
     * Re-reads and re-verifies the license file from disk, falling back to the trial when
     * there is none.
     *
     * <p>The first run is recorded on every refresh, licensed or not. That is what stops
     * the trial from being a loophole: by the time a customer thinks of deleting a paid
     * license to "go back to the trial", the trial recorded on the day they installed the
     * application is long over.
     */
    public LicenseStatus refresh() {
        LocalDate evaluationDate = evaluationDate();
        trialRegistry.recordFirstRun(evaluationDate);

        byte[] envelope = readLicenseFile();
        LicenseStatus fresh = envelope != null
                ? verifier.verify(envelope, fingerprint, evaluationDate)
                : trialRegistry.statusOn(evaluationDate).orElseThrow(this::licenseNotFound);
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
        // The anti-replay rule compares against a real license only. A trial is not one:
        // a customer whose 30 days have barely started must still be able to install a
        // short licence that expires before the trial would have.
        if (current != null && !current.isTrial()
                && !candidate.license().expiresOn().isAfter(current.license().expiresOn())) {
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

    /**
     * The one string to send the publisher to order or renew a license.
     *
     * <p>Preferred over {@link #machineFingerprint()} for that purpose: it carries the same
     * machine identity plus the current expiry date, so the publisher does not have to look
     * up where the previous period ended. See {@link RenewalCode}.
     *
     * <p>Never throws. An installation that cannot evaluate its own license is exactly the
     * one that needs to order one.
     */
    public String renewalCode() {
        return findStatus()
                .map(RenewalCode::of)
                .orElseGet(() -> RenewalCode.ofUnlicensed(fingerprint, evaluationDate()));
    }

    /** Whether license checking is active at all. */
    public boolean isEnabled() {
        return properties.enabled();
    }

    LicenseProperties properties() {
        return properties;
    }

    /**
     * What the installation's own records and data disagree about, if anything.
     *
     * <p>Two independent contradictions, both meaning the same thing -- that what the
     * licence records claim is not what the shop's activity shows:
     * <ul>
     *   <li>the evaluation records say this installation is new, while months of sales
     *       and invoices say otherwise;</li>
     *   <li>the system clock is behind a day this installation has already worked
     *       through, which is what winding it back looks like from the inside.</li>
     * </ul>
     *
     * <p>Surfaced to the user rather than kept in the log. The wording says what was seen
     * and warns that editing the database directly puts their own sales and stock at
     * risk, which is true and is the point: on a machine where someone is doing that, the
     * owner is usually the last to find out. Nothing is ever deleted in response -- the
     * consequence is a shortened trial and a visible warning, never a reprisal against
     * data the customer paid for.
     */
    public Optional<String> integrityWarning() {
        Optional<String> fromRecords = trialRegistry.integrityWarning();
        if (fromRecords.isPresent()) {
            return fromRecords;
        }
        LocalDate systemDate = LocalDate.now(clock);
        LocalDate lastActivity = witness.read().latest();
        if (lastActivity != null && lastActivity.isAfter(systemDate)) {
            return Optional.of("Incoherence detectee : des operations sont enregistrees jusqu'au "
                    + lastActivity + ", alors que la date de cet ordinateur indique le " + systemDate
                    + ". L'horloge du poste a ete reculee. Les tickets et les dates de vente en sont "
                    + "fausses : merci de remettre la date a l'heure. Si vous n'etes pas a l'origine "
                    + "de ce changement, contactez l'editeur.");
        }
        return Optional.empty();
    }

    /**
     * Today, corrected for a clock that has been wound back.
     *
     * <p>Corrected twice, from two sources that fail differently. {@link ClockGuard} keeps
     * a high-water mark in a file, which is precise but can be deleted before the clock is
     * moved. {@link BusinessDataWitness} raises the date to the last day the shop is known
     * to have been trading, which is coarser but has no file to delete: suppressing it
     * means deleting the invoices that prove it.
     */
    private LocalDate evaluationDate() {
        return witness.notBefore(clockGuard.effectiveDate(LocalDate.now(clock)));
    }

    /**
     * The installed envelope, or {@code null} when no file is installed.
     *
     * <p>A missing file is not an error here -- the trial answers for it. A file that is
     * present but unreadable still is: that is a broken installation, not an unlicensed
     * one, and silently handing out a trial would hide it.
     */
    private byte[] readLicenseFile() {
        Path path = installedLicensePath();
        if (path == null) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new LicenseInvalidException(
                    "Le fichier de licence " + path.toAbsolutePath() + " n'a pas pu etre lu.", e);
        }
    }

    /**
     * Where the file actually is: the configured name, or the one the product used to be
     * called before it was renamed to Libero Shop.
     *
     * <p>A shop that was sold {@code liberty-shop.lic} keeps a working licence through the
     * upgrade -- a rename of the product must not turn a paid installation read-only, and the
     * customer has no reason to know the file was ever meant to be called something else. The
     * next renewal is written under the configured name by {@link #install}, so the fallback
     * fades out on its own.
     */
    private Path installedLicensePath() {
        Path configured = properties.path();
        if (Files.isRegularFile(configured)) {
            return configured;
        }
        Path legacy = configured.resolveSibling(LEGACY_LICENSE_FILENAME);
        if (Files.isRegularFile(legacy)) {
            log.info("Licence trouvee sous son ancien nom {} : elle reste valable. Le prochain "
                    + "renouvellement sera ecrit sous {}.", legacy.getFileName(), configured.getFileName());
            return legacy;
        }
        return null;
    }

    /** Raised only when the trial is switched off, which is the strict configuration. */
    private LicenseNotFoundException licenseNotFound() {
        return new LicenseNotFoundException(
                "Aucun fichier de licence trouve a l'emplacement " + properties.path().toAbsolutePath() + ". "
                        + "Merci de communiquer l'empreinte de cette machine a l'editeur pour obtenir "
                        + "une licence : " + fingerprint);
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
