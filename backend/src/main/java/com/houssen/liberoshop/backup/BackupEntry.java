package com.houssen.liberoshop.backup;

import java.time.LocalDateTime;

/**
 * One backup, as its sidecar file describes it.
 *
 * @param fileName      the zip's name, which is also how the screen designates it
 * @param verified      re-opened after writing and found readable; false until then
 * @param invoiceCount  invoices found when verifying -- what the owner checks a restore against
 * @param note          free text: which backup a before-restore copy preceded, an error...
 */
public record BackupEntry(String fileName,
                          LocalDateTime createdAt,
                          BackupKind kind,
                          long sizeBytes,
                          boolean verified,
                          long invoiceCount,
                          long productCount,
                          String note) {

    public BackupEntry withVerification(boolean ok, long invoices, long products, String verificationNote) {
        return new BackupEntry(fileName, createdAt, kind, sizeBytes, ok, invoices, products, verificationNote);
    }
}
