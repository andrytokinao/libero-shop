package com.houssen.liberoshop.web;

import com.houssen.liberoshop.update.UpdateCheckResponse;
import com.houssen.liberoshop.update.UpdateManifest;
import com.houssen.liberoshop.update.UpdateService;
import com.houssen.liberoshop.update.UpdateStatusResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Updating the application: the super-admin's alone, and reached only at {@code /admin/mise-a-jour}
 * -- the screen has no menu entry for it, exactly like the licence page.
 *
 * <p>Applying an update stops and restarts the server, so these answers are sent <em>before</em>
 * the server goes down; the screen then waits for it to come back, as it does for a restore.
 */
@RestController
@RequestMapping("/api/update")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UpdateController {

    private final UpdateService updates;

    public UpdateController(UpdateService updates) {
        this.updates = updates;
    }

    @GetMapping("/status")
    public UpdateStatusResponse status() {
        return updates.status();
    }

    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        UpdateManifest manifest = updates.applyUploaded(file);
        return Map.of("message", "Mise a jour vers la version " + manifest.appVersion()
                + " : le serveur redemarre.", "version", manifest.appVersion());
    }

    /** Looks at the configured URL for a newer version. Does not install anything. */
    @PostMapping("/check")
    public UpdateCheckResponse check() {
        return updates.checkOnline();
    }

    /** Downloads and installs the latest version from the configured URL, then restarts. */
    @PostMapping("/online")
    public Map<String, String> online() {
        UpdateManifest manifest = updates.applyOnline();
        return Map.of("message", "Mise a jour vers la version " + manifest.appVersion()
                + " : le serveur redemarre.", "version", manifest.appVersion());
    }

    @PostMapping("/rollback")
    public Map<String, String> rollback() {
        updates.rollback();
        return Map.of("message", "Retour a la version precedente : le serveur redemarre.");
    }
}
