package com.houssen.libertyshop.license;

import com.houssen.libertyshop.license.exception.LicenseExpiredException;
import com.houssen.libertyshop.license.exception.LicenseInvalidException;
import com.houssen.libertyshop.license.exception.LicenseMachineMismatchException;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDate;

/**
 * Verifies a license envelope: signature, then machine, then dates -- in that order.
 *
 * <p>Pure Java with no Spring and no I/O, so the security-critical logic can be tested
 * exhaustively without a container. {@code LicenseService} owns file access and caching.
 *
 * <p>The ordering is not incidental. Checking the signature first means every later check
 * runs on publisher-authenticated data, and a forged file is rejected as forged rather
 * than as "expired", which keeps the error messages honest.
 */
public class LicenseVerifier {

    private final PublicKey publicKey;

    /**
     * @param publicKey key to check signatures against. Production code always passes
     *                  {@link EmbeddedLicenseKey#publicKey()}; tests pass a throwaway key
     *                  so they can sign their own fixtures. This is a constructor
     *                  parameter rather than a configuration property precisely so a
     *                  customer cannot substitute their own key.
     */
    public LicenseVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Encoded form of the trust anchor. Used to bind auxiliary state (the clock guard
     * record) to this particular build, and safe to expose: a public key is not a secret.
     */
    byte[] trustAnchor() {
        return publicKey.getEncoded();
    }

    /**
     * Full verification.
     *
     * <p>Expiry does not throw: it produces a {@link LicenseState#READ_ONLY} status, so the
     * shop can still consult its sales and stock. Tampering and machine mismatch do throw,
     * because there is no safe degraded mode for a license that was never granted.
     *
     * @param rawEnvelope        raw bytes of the {@code .lic} file
     * @param currentFingerprint fingerprint of this machine
     * @param evaluationDate     the day to evaluate against, already corrected for clock
     *                           rollback by {@link ClockGuard}
     * @throws LicenseInvalidException         malformed file, wrong algorithm, or bad signature
     * @throws LicenseMachineMismatchException license issued for another machine
     */
    public LicenseStatus verify(byte[] rawEnvelope, String currentFingerprint, LocalDate evaluationDate) {
        LicenseFile file = LicenseFile.parse(rawEnvelope);

        if (!EmbeddedLicenseKey.ALGORITHM.equalsIgnoreCase(file.algorithm())) {
            throw new LicenseInvalidException("Algorithme de signature non supporte : " + file.algorithm() + ".");
        }
        if (!isSignatureValid(file)) {
            throw new LicenseInvalidException(
                    "La signature du fichier de licence est invalide. Le fichier a ete modifie ou ne provient "
                            + "pas de l'editeur. Merci de reinstaller le fichier de licence d'origine.");
        }

        License license = file.toLicense();

        if (!license.authorises(currentFingerprint)) {
            throw new LicenseMachineMismatchException(
                    "Cette licence a ete emise pour une autre machine. Empreinte de cette machine : "
                            + currentFingerprint + ". Merci de communiquer cette empreinte a l'editeur pour "
                            + "obtenir une licence valide.",
                    currentFingerprint,
                    license.machineFingerprints());
        }

        return new LicenseStatus(license, license.stateOn(evaluationDate), evaluationDate, currentFingerprint);
    }

    /**
     * Same as {@link #verify} but also rejects an expired license.
     *
     * <p>Used where a fully valid license is a precondition -- notably before overwriting
     * the local file with one downloaded from the renewal endpoint, so a stale response
     * can never replace a license that is still good.
     */
    public LicenseStatus verifyActive(byte[] rawEnvelope, String currentFingerprint, LocalDate evaluationDate) {
        LicenseStatus status = verify(rawEnvelope, currentFingerprint, evaluationDate);
        if (status.state() == LicenseState.READ_ONLY) {
            throw new LicenseExpiredException(status.userMessage(), status.license().expiresOn());
        }
        return status;
    }

    /**
     * Checks the detached signature over the payload bytes.
     *
     * <p>Any {@link GeneralSecurityException} is treated as "invalid" rather than
     * propagated: a malformed signature makes the JDK throw, and that is a rejection, not
     * an internal error.
     */
    private boolean isSignatureValid(LicenseFile file) {
        try {
            Signature verifier = Signature.getInstance(EmbeddedLicenseKey.ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(file.payloadBytes());
            return verifier.verify(file.signature());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
