package com.houssen.liberoshop.license;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * License endpoints for the UI and for support.
 *
 * <p>None of these can weaken the license: the fingerprint and the status are public
 * facts about the installation, and installing a license still requires a signature the
 * customer cannot produce. The handling of an unlicensed installation is the reason
 * {@code /status} and {@code /fingerprint} must stay reachable even when everything else
 * is blocked -- that is how the customer reads the fingerprint to order a license.
 */
@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private final LicenseService licenseService;
    private final ObjectProvider<LicenseRenewalService> renewalService;

    public LicenseController(LicenseService licenseService, ObjectProvider<LicenseRenewalService> renewalService) {
        this.licenseService = licenseService;
        this.renewalService = renewalService;
    }

    /** Current license state, for the renewal banner in the UI. */
    @GetMapping("/status")
    public LicenseStatusResponse status() {
        return licenseService.findStatus()
                .map(status -> new LicenseStatusResponse(
                        // An installation running on the trial is not a licensed one, and
                        // the banner must not tell the customer otherwise.
                        !status.isTrial(),
                        status.state().name(),
                        status.writesAllowed(),
                        status.license().customerName(),
                        status.license().customerId(),
                        status.license().plan().name(),
                        status.license().expiresOn(),
                        status.daysUntilExpiry(),
                        status.daysUntilReadOnly(),
                        status.userMessage(),
                        status.fingerprint(),
                        licenseService.integrityWarning().orElse(null)))
                .orElseGet(() -> new LicenseStatusResponse(
                        false, "UNLICENSED", !licenseService.isEnabled(),
                        null, null, null, null, 0, 0,
                        "Aucune licence valide n'est installee sur cette machine. Merci de contacter "
                                + "l'editeur en communiquant l'empreinte ci-dessous.",
                        licenseService.machineFingerprint(),
                        licenseService.integrityWarning().orElse(null)));
    }

    /**
     * The machine fingerprint. This is the value the customer sends to the publisher to
     * have a license issued, so it is intentionally trivial to obtain.
     */
    @GetMapping("/fingerprint")
    public FingerprintResponse fingerprint() {
        return FingerprintResponse.of(licenseService.machineFingerprint());
    }

    /**
     * The renewal code: what the customer actually sends to order or renew a license.
     *
     * <p>Superset of {@code /fingerprint}, which stays for the case where someone is
     * reading a value out over the phone. This one carries the current expiry date too, so
     * the publisher issues the renewal without looking anything up -- see
     * {@link RenewalCode}.
     */
    @GetMapping("/renewal-code")
    public RenewalCodeResponse renewalCode() {
        return RenewalCodeResponse.of(licenseService.renewalCode());
    }

    /**
     * Installs a license file received out of band -- typically emailed to a customer
     * whose shop has no usable internet link at all.
     *
     * <p>Safe to expose: the content must carry a valid publisher signature for this very
     * machine, and it must expire later than the license already installed.
     */
    @PostMapping(value = "/install", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public LicenseStatusResponse install(@RequestBody String licenseFileContent) {
        licenseService.install(licenseFileContent.getBytes(StandardCharsets.UTF_8));
        return status();
    }

    /**
     * Triggers an immediate online renewal check, for the customer who has just paid and
     * does not want to wait for the next scheduled attempt.
     */
    @PostMapping("/renew")
    public ResponseEntity<LicenseStatusResponse> renew() {
        LicenseRenewalService service = renewalService.getIfAvailable();
        if (service == null) {
            // Renewal is not configured on this installation; not an error, just nothing to do.
            return ResponseEntity.status(501).body(status());
        }
        service.tryRenew();
        return ResponseEntity.ok(status());
    }

    /**
     * Flat view of the license state, shaped for the UI rather than for storage.
     *
     * @param integrityWarning what the installation's records and its own data disagree
     *                         about, or {@code null} when they agree. Carried on the same
     *                         payload as the rest on purpose: the screen that shows how
     *                         many days are left is the one that should say when that
     *                         count rests on something that does not add up
     */
    public record LicenseStatusResponse(
            boolean licensed,
            String state,
            boolean writesAllowed,
            String customerName,
            String customerId,
            String plan,
            LocalDate expiresOn,
            long daysUntilExpiry,
            long daysUntilReadOnly,
            String message,
            String machineFingerprint,
            String integrityWarning) {
    }

    /** The renewal code plus the wording shown next to it in the UI. */
    public record RenewalCodeResponse(String renewalCode, List<String> instructions) {

        static RenewalCodeResponse of(String renewalCode) {
            return new RenewalCodeResponse(renewalCode, List.of(
                    "Envoyez ce code a l'editeur pour commander ou renouveler votre licence.",
                    "Il contient l'identifiant de cet ordinateur et la date de fin de votre licence "
                            + "actuelle, rien d'autre : ni nom, ni chiffre d'affaires, ni donnee de vente.",
                    "Vous recevrez en retour un fichier de licence a installer depuis cet ecran."));
        }
    }

    /** The fingerprint plus the wording shown next to it in the UI. */
    public record FingerprintResponse(String machineFingerprint, List<String> instructions) {

        static FingerprintResponse of(String machineFingerprint) {
            return new FingerprintResponse(machineFingerprint, List.of(
                    "Communiquez cette empreinte a l'editeur pour obtenir votre fichier de licence.",
                    "Elle est specifique a cet ordinateur et ne contient aucune donnee personnelle."));
        }
    }
}
