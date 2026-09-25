/**
 * Mirrors com.houssen.libertyshop.entity.Enums and RoleApp.
 * Values are the strings persisted by @Enumerated(EnumType.STRING).
 */

export enum PaymentStatus {
  PAID = 'PAID',
  UNPAID = 'UNPAID',
}

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

export const ROLE_LABELS: Record<RoleApp, string> = {
  [RoleApp.CASHIER]: 'Responsable de caisse',
  [RoleApp.DEPOT_AGENT]: 'Agent de dépôt',
  [RoleApp.DEPOT_MANAGER]: 'Responsable entrée-sortie dépôt',
  [RoleApp.SUPER_ADMIN]: 'Super admin',
};
