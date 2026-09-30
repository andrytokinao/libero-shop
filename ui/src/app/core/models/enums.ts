/**
 * Mirrors com.houssen.liberoshop.entity.Enums and RoleApp.
 * Values are the strings persisted by @Enumerated(EnumType.STRING).
 */

/**
 * Where the money for a sale is. Unpaid at the desk, it walks UNPAID → COLLECTED (paid to the
 * storekeeper) → REMITTED (brought to the desk) → PAID (counted and confirmed by a cashier).
 */
export enum PaymentStatus {
  PAID = 'PAID',
  UNPAID = 'UNPAID',
  /** Paid to the storekeeper on hand-over; the cash is still with them. */
  COLLECTED = 'COLLECTED',
  /** Brought to the desk; a cashier has yet to confirm receiving it. */
  REMITTED = 'REMITTED',
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  [PaymentStatus.PAID]: 'Payée',
  [PaymentStatus.UNPAID]: 'Non payée',
  [PaymentStatus.COLLECTED]: 'Encaissée au dépôt',
  [PaymentStatus.REMITTED]: 'Versée, à confirmer',
};

export enum DeliveryStatus {
  PENDING = 'PENDING',
  DELIVERED = 'DELIVERED',
}

export enum RemittanceStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
}

export enum PaymentMethod {
  CASH = 'CASH',
  MOBILE_MONEY = 'MOBILE_MONEY',
  BANK_TRANSFER = 'BANK_TRANSFER',
  OTHER = 'OTHER',
}

export enum RoleApp {
  CASHIER = 'CASHIER',
  DEPOT_AGENT = 'DEPOT_AGENT',
  DEPOT_MANAGER = 'DEPOT_MANAGER',
  SUPER_ADMIN = 'SUPER_ADMIN',
}

/** Discriminator of the stock_movement single table. */
export enum MovementType {
  SUPPLY = 'SUPPLY',
  OUTPUT = 'OUTPUT',
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  [PaymentMethod.CASH]: 'Espèces',
  [PaymentMethod.MOBILE_MONEY]: 'Mobile money',
  [PaymentMethod.BANK_TRANSFER]: 'Virement bancaire',
  [PaymentMethod.OTHER]: 'Autre',
};

/**
 * Order the roles are listed in everywhere, mirroring RolePolicy.PRECEDENCE on the server:
 * the counter first, because whoever holds every role in a small grocery opens the day by
 * selling, not by reading a report.
 */
export const ROLE_PRECEDENCE: readonly RoleApp[] = [
  RoleApp.CASHIER,
  RoleApp.DEPOT_AGENT,
  RoleApp.DEPOT_MANAGER,
  RoleApp.SUPER_ADMIN,
];

/** The given roles in that order, so every list of them reads the same way. */
export function orderRoles(roles: readonly RoleApp[]): RoleApp[] {
  return ROLE_PRECEDENCE.filter((role) => roles.includes(role));
}

export const ROLE_LABELS: Record<RoleApp, string> = {
  [RoleApp.CASHIER]: 'Responsable de caisse',
  [RoleApp.DEPOT_AGENT]: 'Agent de dépôt',
  [RoleApp.DEPOT_MANAGER]: 'Responsable entrée-sortie dépôt',
  [RoleApp.SUPER_ADMIN]: 'Super admin',
};

/** One word per role, for the accounts that hold several. Mirrors RolePolicy.shortLabelOf. */
export const ROLE_SHORT_LABELS: Record<RoleApp, string> = {
  [RoleApp.CASHIER]: 'Caisse',
  [RoleApp.DEPOT_AGENT]: 'Dépôt',
  [RoleApp.DEPOT_MANAGER]: 'Stock',
  [RoleApp.SUPER_ADMIN]: 'Admin',
};

/**
 * Names the job of an account: the full title when it has one, the short labels when it has
 * several. Same rule as RolePolicy.labelOf on the server, which is what the session carries
 * — this one is for the lists the server sends as raw roles.
 */
export function describeRoles(roles: readonly RoleApp[]): string {
  if (roles.length === 1) {
    return ROLE_LABELS[roles[0]];
  }
  return roles.map((role) => ROLE_SHORT_LABELS[role]).join(' · ');
}
