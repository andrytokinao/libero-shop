package com.houssen.libertyshop.license;

import com.houssen.libertyshop.license.exception.LicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;

import java.io.PrintStream;
import java.time.Clock;

/**
 * Checks the license as early as the Spring lifecycle allows.
 *
 * <p>Hooked on {@code ApplicationEnvironmentPreparedEvent}: configuration files have been
 * loaded, so the license path is known, but no bean has been created and no database
 * connection has been opened yet. A tampered license therefore stops the boot before the
 * application does anything at all.
 *
 * <p>Registered explicitly from {@code BackendApplication.main} rather than through
 * {@code spring.factories}. That keeps the wiring visible in one place and, just as
 * usefully, keeps it out of {@code @SpringBootTest}, which does not go through
 * {@code main} -- integration tests then never need a real license file.
 *
 * <p>Only tampering aborts the boot. An expired license is logged and the application
 * starts read-only, because a shop that cannot consult yesterday's sales is worse off
 * than a publisher who waits a few days to be paid. A license that is simply absent falls
 * back to the evaluation period, unless the trial has been switched off -- then, and only
 * then, a missing file is fatal again.
 */
public class LicenseStartupListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LicenseStartupListener.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        LicenseProperties properties = Binder.get(event.getEnvironment())
                .bind("libertyshop.license", LicenseProperties.class)
                .orElseGet(() -> new LicenseProperties(null, null, null, null, null));

        if (!properties.enabled()) {
            log.warn("License checking is DISABLED (libertyshop.license.enabled=false).");
            return;
        }

        String fingerprint = MachineFingerprint.current();
        log.info("Machine fingerprint: {}", fingerprint);

        LicenseService service = new LicenseService(
                new LicenseVerifier(EmbeddedLicenseKey.publicKey()), properties, Clock.systemDefaultZone(),
                fingerprint);
        try {
            LicenseStatus status = service.refresh();
            if (status.state() == LicenseState.ACTIVE) {
                log.info("License valid until {} ({} day(s) left).",
                        status.license().expiresOn(), status.daysUntilExpiry());
            } else if (status.isTrial()) {
                // Printed, not logged: an unlicensed installation is exactly the situation
                // where the shop owner has to read the fingerprint off the screen.
                printBanner(System.out, "LIBERTY SHOP - PERIODE D'ESSAI", status.userMessage(),
                        properties, fingerprint);
            } else {
                log.warn("{}", status.userMessage());
            }
        } catch (LicenseException e) {
            // Printed rather than only logged: at this point the logging system may still
            // be starting, and this message is what the shop owner needs to read.
            printBanner(System.err, "LIBERTY SHOP - DEMARRAGE IMPOSSIBLE (" + e.code() + ")",
                    e.getMessage(), properties, fingerprint);
            throw e;
        }
    }

    /** The console box the shop owner reads: what happened, where, and which fingerprint to send. */
    private static void printBanner(PrintStream out, String title, String message,
                                    LicenseProperties properties, String fingerprint) {
        String rule = "=======================================================================";
        out.println();
        out.println(rule);
        out.println(" " + title);
        out.println(rule);
        out.println(" " + message);
        out.println();
        out.println(" Fichier attendu   : " + properties.path().toAbsolutePath());
        out.println(" Empreinte machine : " + fingerprint);
        out.println(rule);
        out.println();
    }
}
