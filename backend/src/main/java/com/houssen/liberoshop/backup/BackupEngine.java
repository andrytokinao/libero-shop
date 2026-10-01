package com.houssen.liberoshop.backup;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Copies the live database and checks a copy can be read back.
 *
 * <p>The port {@link BackupService} depends on: it knows nothing of H2's {@code BACKUP} command,
 * and moving to another database is writing another implementation of this.
 */
public interface BackupEngine {

    /** A consistent copy of the database, taken while the shop keeps working. */
    void snapshot(Path zip) throws IOException;

    /** Opens the copy apart from the live database and counts what it holds. */
    Verification verify(Path zip);

    /**
     * @param error null when readable; otherwise why not
     */
    record Verification(boolean readable, long invoices, long products, String error) {
    }
}
