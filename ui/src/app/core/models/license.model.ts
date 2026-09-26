/**
 * Mirrors com.houssen.liberoshop.license — LicenseState, and the three payloads
 * LicenseController hands to the UI.
 *
 * <p>Every date arithmetic already happened server-side: `daysUntilExpiry`,
 * `daysUntilReadOnly` and the French `message` arrive computed. The screen re-implements
 * none of it, which is what keeps the browser's clock out of the question entirely.
 */

/**
 * What the installation is allowed to do today.
 *
 * <p>`UNLICENSED` is not a server-side `LicenseState`: it is what `/status` answers when
 * no status can be evaluated at all. It is folded in here because the UI has to render
 * that case like any other.
 */
export enum LicenseState {
  /** Before the expiry date: everything works. */
  ACTIVE = 'ACTIVE',
  /** Past expiry, inside the grace window: everything still works, with a warning. */
  GRACE = 'GRACE',
  /** Past the grace window: browsing stays available, writes are refused. */
  READ_ONLY = 'READ_ONLY',
  /** No license installed, inside the one-off evaluation period. */
  TRIAL = 'TRIAL',
  /** The evaluation period is over and no license was ever installed. */
  TRIAL_EXPIRED = 'TRIAL_EXPIRED',
  /** No status at all — no file, or the check is switched off in development. */
  UNLICENSED = 'UNLICENSED',
}

/** Billing cycle the license was sold under. Informational: expiry is what enforces. */
export enum LicensePlan {
  MONTHLY = 'MONTHLY',
  ANNUAL = 'ANNUAL',
  TRIAL = 'TRIAL',
}

/** GET /api/license/status — flat view shaped for the UI rather than for storage. */
export interface LicenseStatus {
  /** False during the evaluation period: an unlicensed machine is never shown as licensed. */
  licensed: boolean;
  state: LicenseState;
  /** Whether sales, invoices and stock movements may still be recorded. */
  writesAllowed: boolean;
  customerName: string | null;
  customerId: string | null;
  plan: LicensePlan | null;
  /** ISO local date, `yyyy-MM-dd`. Null when nothing is installed. */
  expiresOn: string | null;
  /** Zero on the expiry day itself, negative once past it. */
  daysUntilExpiry: number;
  /** Zero or less once the installation has turned read-only. */
  daysUntilReadOnly: number;
  /** Written in French by the server, for shop staff. Shown as-is. */
  message: string;
  machineFingerprint: string;
}

/** GET /api/license/renewal-code — the code, plus the wording shown next to it. */
export interface RenewalCodeResponse {
  renewalCode: string;
  instructions: string[];
}

/** GET /api/license/fingerprint — for an order dictated over the phone. */
export interface FingerprintResponse {
  machineFingerprint: string;
  instructions: string[];
}

/** How loudly the screen should ask for a renewal. Maps onto the badge colours. */
export type LicenseSeverity = 'ok' | 'warn' | 'danger';

/**
 * Days before expiry at which the banner starts asking for a renewal.
 *
 * <p>Same figure as the backend's `liberoshop.license.renewal.urgent-within-days`: the
 * screen begins insisting on the day the online check starts trying every day.
 */
export const RENEWAL_WARNING_DAYS = 30;

export const LICENSE_STATE_LABELS: Record<LicenseState, string> = {
  [LicenseState.ACTIVE]: 'Licence active',
  [LicenseState.GRACE]: 'Expirée — période de tolérance',
  [LicenseState.READ_ONLY]: 'Lecture seule',
  [LicenseState.TRIAL]: "Période d'essai",
  [LicenseState.TRIAL_EXPIRED]: "Essai terminé — lecture seule",
  [LicenseState.UNLICENSED]: 'Aucune licence',
};

export const LICENSE_PLAN_LABELS: Record<LicensePlan, string> = {
  [LicensePlan.MONTHLY]: 'Mensuel',
  [LicensePlan.ANNUAL]: 'Annuel',
  [LicensePlan.TRIAL]: 'Évaluation',
};
