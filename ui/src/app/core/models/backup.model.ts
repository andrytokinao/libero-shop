/** Mirrors the backup package: BackupConfig, BackupKind and BackupOverviewResponse. */

export enum BackupKind {
  AUTOMATIC = 'AUTOMATIC',
  MANUAL = 'MANUAL',
  STARTUP = 'STARTUP',
  BEFORE_RESTORE = 'BEFORE_RESTORE',
}

export const BACKUP_KIND_LABELS: Record<BackupKind, string> = {
  [BackupKind.AUTOMATIC]: 'Automatique',
  [BackupKind.MANUAL]: 'Manuelle',
  [BackupKind.STARTUP]: 'Au démarrage',
  [BackupKind.BEFORE_RESTORE]: 'Avant restauration',
};

/** How often, how many kept, where. Stored beside the database, not in it. */
export interface BackupConfig {
  enabled: boolean;
  intervalMinutes: number;
  /** The most recent automatic backups kept, whatever their age. */
  keepRecent: number;
  /** Days for which the day's last automatic backup is kept. */
  keepDaily: number;
  /** Months for which the month's last automatic backup is kept. */
  keepMonthly: number;
  /** Manual and before-restore backups kept. */
  keepManual: number;
  directory: string;
  /** A USB key, a second disk, another machine's shared folder; null for none. */
  secondaryDirectory: string | null;
}

export interface BackupItem {
  fileName: string;
  createdAt: string;
  kind: BackupKind;
  sizeBytes: number;
  /** Re-opened after writing and found readable. */
  verified: boolean;
  invoiceCount: number;
  productCount: number;
  note: string | null;
  inSecondary: boolean;
}

export interface BackupStatus {
  lastSuccessAt: string | null;
  nextAutomaticAt: string | null;
  lastError: string | null;
  lastErrorAt: string | null;
  /** No backup has succeeded for too long. */
  stale: boolean;
  databaseFile: string;
  databaseSizeBytes: number;
  freeSpaceBytes: number;
  secondaryConfigured: boolean;
  /** False when the USB key was taken away. */
  secondaryReachable: boolean;
  secondaryError: string | null;
  /** The server can restart itself to carry out a restore. */
  canRestore: boolean;
}

/** GET /api/backups */
export interface BackupOverview {
  /** False when the database is not a file this server can copy. */
  available: boolean;
  status: BackupStatus | null;
  config: BackupConfig | null;
  backups: BackupItem[];
  /** The intervals the server accepts, in minutes. */
  intervals: number[];
}

/** "15 min", "1 h", "1 fois par jour". */
export function intervalLabel(minutes: number): string {
  if (minutes >= 1440) {
    return '1 fois par jour';
  }
  return minutes < 60 ? `toutes les ${minutes} min` : `toutes les ${minutes / 60} h`;
}

export function sizeLabel(bytes: number): string {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(1)} Go`;
  }
  if (bytes >= 1024 ** 2) {
    return `${(bytes / 1024 ** 2).toFixed(1)} Mo`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
}
