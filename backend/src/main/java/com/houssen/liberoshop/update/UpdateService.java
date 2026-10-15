package com.houssen.liberoshop.update;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Orchestrates application updates: reports status, and applies a {@code .lsu} an administrator
 * uploads by hand. Verification lives in {@link UpdateVerifier}, the swap in {@link UpdateInstaller};
 * this service ties them to the running version and to the uploaded bytes.
 */
@Service
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private final UpdateVerifier verifier;
    private final UpdateInstaller installer;
    private final UpdateSource source;
    private final UpdateProperties properties;
    private final String currentVersion;

    public UpdateService(UpdateVerifier verifier, UpdateInstaller installer, UpdateSource source,
                         UpdateProperties properties, ObjectProvider<BuildProperties> build) {
        this.verifier = verifier;
        this.installer = installer;
        this.source = source;
        this.properties = properties;
        BuildProperties info = build.getIfAvailable();
        // Absent when run from the IDE without a Maven build: said, not invented, as elsewhere.
        this.currentVersion = info == null ? "dev" : info.getVersion();
    }

    public String currentVersion() {
        return currentVersion;
    }

    public UpdateStatusResponse status() {
        return new UpdateStatusResponse(
                currentVersion,
                installer.canApply(),
                installer.unavailableReason(),
                installer.canRollback(),
                properties.automaticEnabled(),
                properties.hasUrl(),
                properties.checkTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                properties.requireConfirmation());
    }

    /**
     * Verifies an uploaded {@code .lsu} and, if it is genuinely newer and this is an install that
     * can be updated in place, applies it -- after which the server stops and comes back on the new
     * version. The verified manifest is returned so the caller can answer before the server goes.
     */
    public UpdateManifest applyUploaded(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("UPDATE_EMPTY", "Aucun fichier de mise a jour n'a ete envoye.");
        }
        if (!installer.canApply()) {
            throw new BusinessRuleException("UPDATE_UNAVAILABLE", installer.unavailableReason());
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("liberoshop-upload-", ".lsu");
            file.transferTo(temp);
            UpdatePackage pkg = UpdatePackage.open(temp);
            UpdateManifest manifest = verifier.verifyNewerThan(pkg, currentVersion);
            installer.apply(pkg);
            log.warn("Mise a jour {} acceptee depuis un envoi manuel.", manifest.appVersion());
            return manifest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // apply() has already staged the payload; the uploaded copy is no longer needed.
            deleteQuietly(temp);
        }
    }

    /** Looks at the configured URL and reports whether a newer version is published there. */
    public UpdateCheckResponse checkOnline() {
        RemoteUpdateInfo info = source.fetchManifest();
        return new UpdateCheckResponse(
                Versions.isNewer(info.latestVersion(), currentVersion),
                info.latestVersion(),
                info.releasedOn(),
                info.notes(),
                currentVersion);
    }

    /**
     * Downloads the latest {@code .lsu} from the configured URL and, if it is genuinely newer and
     * can be applied here, applies it. The downloaded copy is verified exactly like an uploaded
     * one -- the manifest only says where to look, never what to trust.
     */
    public UpdateManifest applyOnline() {
        RemoteUpdateInfo info = source.fetchManifest();
        if (!Versions.isNewer(info.latestVersion(), currentVersion)) {
            throw new BusinessRuleException("UPDATE_UP_TO_DATE",
                    "L'application est deja a jour (version " + currentVersion + ").");
        }
        if (!installer.canApply()) {
            throw new BusinessRuleException("UPDATE_UNAVAILABLE", installer.unavailableReason());
        }
        Path lsu = source.download(info);
        try {
            UpdatePackage pkg = UpdatePackage.open(lsu);
            UpdateManifest manifest = verifier.verifyNewerThan(pkg, currentVersion);
            installer.apply(pkg);
            log.warn("Mise a jour {} acceptee depuis le serveur de mise a jour.", manifest.appVersion());
            return manifest;
        } finally {
            deleteQuietly(lsu);
        }
    }

    public void rollback() {
        installer.rollback();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Fichier temporaire de mise a jour non supprime : {}", path, e);
        }
    }
}
