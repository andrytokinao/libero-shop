package com.houssen.liberoshop.backup;

/** Why a backup was made, which decides how long it is kept (see {@link RetentionPolicy}). */
public enum BackupKind {
    /** On schedule, while the shop works. */
    AUTOMATIC,
    /** "Sauvegarder maintenant". */
    MANUAL,
    /** Copied at startup, before the schema is updated: undoes an upgrade gone wrong. */
    STARTUP,
    /** The database as it was just before a restore replaced it: undoes the restore itself. */
    BEFORE_RESTORE
}
