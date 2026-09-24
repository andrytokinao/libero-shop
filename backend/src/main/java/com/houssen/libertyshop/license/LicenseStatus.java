package com.houssen.libertyshop.license;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Snapshot of the license evaluation: the license itself plus what it means today.
 *
 * <p>Returned by {@code LicenseService.getStatus()} and exposed over HTTP so the UI can
 * show a renewal banner without re-implementing any of the date arithmetic.
 *
 * @param license    the verified license
 * @param state      what the application is allowed to do
 * @param evaluatedOn the day the evaluation was made (may be ahead of the system clock
 *                   if a clock rollback was detected)
 * @param fingerprint the fingerprint of the machine that was checked
 */
public record LicenseStatus(
        License license,
        LicenseState state,
        LocalDate evaluatedOn,
        String fingerprint) {

    /** Days left before expiry; zero on the expiry day itself, negative once past it. */
    public long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(evaluatedOn, license.expiresOn());
    }

    /** Days left before the application turns read-only; zero or less once it has. */
    public long daysUntilReadOnly() {
        return ChronoUnit.DAYS.between(evaluatedOn, license.readOnlyFrom());
    }

    /** Whether writes (sales, invoices, stock movements) are currently permitted. */
    public boolean writesAllowed() {
        return state.allowsWrites();
    }

    /** Whether this is the local evaluation period rather than a license from the publisher. */
    public boolean isTrial() {
        return state.isTrial();
    }

    /**
     * Message for the end user. Written in French because it is shown directly to shop
     * staff, unlike the rest of the code and logs which stay in English.
     */
    public String userMessage() {
        return switch (state) {
            case ACTIVE -> "Licence active jusqu'au " + license.expiresOn() + " ("
                    + daysUntilExpiry() + " jour(s) restant(s)).";
            case GRACE -> "Licence expiree le " + license.expiresOn()
                    + ". L'application reste utilisable pendant encore " + daysUntilReadOnly()
                    + " jour(s). Merci de proceder au renouvellement pour eviter le passage en lecture seule.";
            case READ_ONLY -> "Licence expiree le " + license.expiresOn()
                    + ". L'application est en lecture seule : la consultation des ventes, du stock et des "
                    + "factures reste possible, mais aucune nouvelle operation ne peut etre enregistree. "
                    + "Merci de renouveler votre licence.";
            case TRIAL -> "Periode d'essai en cours jusqu'au " + license.expiresOn() + " ("
                    + daysUntilExpiry() + " jour(s) restant(s)). Aucune licence n'est installee sur cette "
                    + "machine : merci de communiquer l'empreinte " + fingerprint + " a l'editeur avant la "
                    + "fin de l'essai.";
            case TRIAL_EXPIRED -> "La periode d'essai s'est terminee le " + license.expiresOn()
                    + ". L'application est en lecture seule : la consultation des ventes, du stock et des "
                    + "factures reste possible, mais aucune nouvelle operation ne peut etre enregistree. "
                    + "L'essai n'est accorde qu'une seule fois par machine : merci d'installer votre "
                    + "licence (empreinte " + fingerprint + ").";
        };
    }
}
