package com.houssen.libertyshop.license;

import com.houssen.libertyshop.license.exception.LicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;

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
 * than a publisher who waits a few days to be paid.
 */
public class LicenseStartupListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LicenseStartupListener.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        LicenseProperties properties = Binder.get(event.getEnvironment())
                .bind("libertyshop.license", LicenseProperties.class)
                .orElseGet(() -> new LicenseProperties(null, null, null, null));

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
            } else {
                log.warn("{}", status.userMessage());
            }
        } catch (LicenseException e) {
            // Printed rather than only logged: at this point the logging system may still
            // be starting, and this message is what the shop owner needs to read.
            System.err.println();
            System.err.println("=======================================================================");
            System.err.println(" LIBERTY SHOP - DEMARRAGE IMPOSSIBLE (" + e.code() + ")");
            System.err.println("=======================================================================");
            System.err.println(" " + e.getMessage());
            System.err.println();
            System.err.println(" Fichier attendu   : " + properties.path().toAbsolutePath());
            System.err.println(" Empreinte machine : " + fingerprint);
            System.err.println("=======================================================================");
            System.err.println();
            throw e;
        }
    }
}
