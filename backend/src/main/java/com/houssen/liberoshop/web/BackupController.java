package com.houssen.liberoshop.web;

import com.houssen.liberoshop.backup.BackupConfig;
import com.houssen.liberoshop.backup.BackupEntry;
import com.houssen.liberoshop.backup.BackupKind;
import com.houssen.liberoshop.backup.BackupService;
import com.houssen.liberoshop.web.dto.BackupOverviewResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * The database's backups: the super-admin's alone -- a backup is the whole shop, sales and
 * accounts included, and a restore rewinds it.
 */
@RestController
@RequestMapping("/api/backups")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BackupController {

    private final BackupService backups;

    public BackupController(BackupService backups) {
        this.backups = backups;
    }

    @GetMapping
    public BackupOverviewResponse overview() {
        return backups.overview();
    }

    @PostMapping("/now")
    public BackupEntry backupNow() {
        return backups.backupNow(BackupKind.MANUAL, "Sauvegarde manuelle.");
    }

    @PutMapping("/settings")
    public BackupConfig updateSettings(@RequestBody BackupConfig config) {
        return backups.updateConfig(config);
    }

    @GetMapping("/{fileName}/file")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path file = backups.fileOf(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName().toString()).build().toString())
                .body(new FileSystemResource(file));
    }

    /** Answers before the restart: the screen then waits for the server to come back. */
    @PostMapping("/{fileName}/restore")
    public Map<String, String> restore(@PathVariable String fileName) {
        backups.restore(fileName);
        return Map.of("message", "Restauration de " + fileName + " : le serveur redémarre.");
    }
}
