package com.houssen.liberoshop.update;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Verifies an update package: signature first, then the payload digest, then -- when asked -- that
 * it is strictly newer than what runs now.
 *
 * <p>Pure Java, no Spring, no I/O beyond the package it is handed, so the security-critical logic
 * can be tested without a container. The signature is checked <em>before</em> anything else, so
 * every later check runs on publisher-authenticated bytes and a forged file is rejected as forged.
 */
public class UpdateVerifier {

    private final PublicKey publicKey;

    /**
     * @param publicKey key to check signatures against. Production passes
     *                  {@link EmbeddedUpdateKey#publicKey()}; tests pass a throwaway key so they
     *                  can sign their own fixtures. A constructor parameter, not a configuration
     *                  property, precisely so a customer cannot substitute their own key.
     */
    public UpdateVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Full authenticity check, independent of the running version. Verifies the Ed25519 signature
     * over the manifest bytes, then that the payload's SHA-256 matches the signed digest.
     *
     * @return the trusted manifest
     * @throws UpdateInvalidException wrong algorithm, bad signature, or payload/digest mismatch
     */
    public UpdateManifest verify(UpdatePackage pkg) {
        if (!EmbeddedUpdateKey.ALGORITHM.equalsIgnoreCase(pkg.algorithm())) {
            throw new UpdateInvalidException("Algorithme de signature non supporte : " + pkg.algorithm() + ".");
        }
        if (!isSignatureValid(pkg)) {
            throw new UpdateInvalidException(
                    "La signature de la mise a jour est invalide. Le fichier a ete modifie ou ne provient pas de "
                            + "l'editeur. Merci de telecharger a nouveau la mise a jour d'origine.");
        }
        UpdateManifest manifest = UpdateManifest.parse(pkg.manifestBytes());
        if (!manifest.payloadSha256().equalsIgnoreCase(pkg.payloadSha256())) {
            throw new UpdateInvalidException(
                    "Le contenu de la mise a jour ne correspond pas a sa signature. Fichier incomplet ou altere.");
        }
        return manifest;
    }

    /**
     * Same as {@link #verify} but also rejects an update that is not strictly newer than
     * {@code currentVersion}. Used before applying, so a replayed or older file can never roll the
     * installation backwards.
     */
    public UpdateManifest verifyNewerThan(UpdatePackage pkg, String currentVersion) {
        UpdateManifest manifest = verify(pkg);
        if (!Versions.isNewer(manifest.appVersion(), currentVersion)) {
            throw new UpdateInvalidException("Cette mise a jour (version " + manifest.appVersion()
                    + ") n'est pas plus recente que la version installee (" + currentVersion + ").");
        }
        return manifest;
    }

    private boolean isSignatureValid(UpdatePackage pkg) {
        try {
            Signature verifier = Signature.getInstance(EmbeddedUpdateKey.ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(pkg.manifestBytes());
            return verifier.verify(pkg.signature());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
