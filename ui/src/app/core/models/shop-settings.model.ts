/**
 * Mirrors com.houssen.liberoshop.entity.BusinessType and ShopSettingsResponse: how the shop
 * works, sent with the session so every screen follows it from the first page on.
 */

export enum BusinessType {
  COUNTER = 'COUNTER',
  GROCERY_WITH_DEPOT = 'GROCERY_WITH_DEPOT',
  WHOLESALE_DEPOT = 'WHOLESALE_DEPOT',
  RESTAURANT = 'RESTAURANT',
  BAR = 'BAR',
  HOTEL = 'HOTEL',
}

/** The switches; the server reconciles the ones that depend on each other. */
export interface ShopFeatures {
  /** Orders wait to be handed over (depot, kitchen, bar); off, a sale leaves with the customer. */
  separateDelivery: boolean;
  /** Whoever hands an unpaid order over takes the money, and brings it to the till later. */
  payAtDepot: boolean;
  /** Cash brought to the till is confirmed by someone other than the person who brought it. */
  dualControlRemittance: boolean;
  /** An unpaid order already handed over may still be cancelled, to record what happened. */
  cancelAfterDelivery: boolean;
  /** Whoever takes orders may take the customer's cash too, and brings it to the till. */
  orderTakerCollects: boolean;
  /** Customers order from their phone by scanning their table's QR code. */
  onlineOrdering: boolean;
}

/** GET /api/settings, and the `settings` of the session. */
export interface ShopSettings extends ShopFeatures {
  businessType: BusinessType;
}

/** PUT /api/settings — the whole configuration as the screen shows it. */
export type UpdateShopSettingsRequest = ShopSettings;

/** The words a business uses for the same things. */
export interface Vocabulary {
  /** Who prepares and hands the orders over: the depot module's name. */
  depot: string;
  /** Its screen of orders to hand over. */
  handOver: string;
  /** The button that hands one order over: "Remettre au client" in a shop, "Servir" at a table. */
  handOverAction: string;
  /** The orders waiting for it, in two words: "À remettre", "À servir". */
  pendingHandOver: string;
  /** Who an order is for. */
  client: string;
  /** What the till writes when nobody is named. */
  clientPlaceholder: string;
}

export interface BusinessTypeInfo {
  type: BusinessType;
  label: string;
  icon: string;
  description: string;
  /** Mirrors BusinessType.defaults() on the server: what picking the type ticks. */
  defaults: ShopFeatures;
  vocabulary: Vocabulary;
}

const SHOP_WORDS: Vocabulary = {
  depot: 'Dépôt',
  handOver: 'Remise de commande',
  handOverAction: 'Remettre au client',
  pendingHandOver: 'À remettre',
  client: 'Client',
  clientPlaceholder: 'Client comptoir',
};

export const BUSINESS_TYPES: readonly BusinessTypeInfo[] = [
  {
    type: BusinessType.COUNTER,
    label: 'Comptoir',
    icon: '◫',
    description:
      'Une seule caisse : le client paie et repart avec ses articles.',
    defaults: {
      separateDelivery: false,
      payAtDepot: false,
      dualControlRemittance: false,
      cancelAfterDelivery: false,
      orderTakerCollects: false,
      onlineOrdering: false,
    },
    vocabulary: SHOP_WORDS,
  },
  {
    type: BusinessType.GROCERY_WITH_DEPOT,
    label: 'Épicerie avec dépôt',
    icon: '⇥',
    description:
      'La caisse vend, le dépôt remet la marchandise et peut encaisser.',
    defaults: {
      separateDelivery: true,
      payAtDepot: true,
      dualControlRemittance: false,
      cancelAfterDelivery: false,
      orderTakerCollects: false,
      onlineOrdering: false,
    },
    vocabulary: SHOP_WORDS,
  },
  {
    type: BusinessType.WHOLESALE_DEPOT,
    label: 'Dépôt de gros',
    icon: '▢',
    description:
      "Plusieurs employés : l'argent du dépôt est recompté par un caissier.",
    defaults: {
      separateDelivery: true,
      payAtDepot: true,
      dualControlRemittance: true,
      cancelAfterDelivery: false,
      orderTakerCollects: false,
      onlineOrdering: false,
    },
    vocabulary: SHOP_WORDS,
  },
  {
    type: BusinessType.RESTAURANT,
    label: 'Restaurant',
    icon: '◎',
    description: "La cuisine prépare et sert, l'addition se règle à la caisse.",
    defaults: {
      separateDelivery: true,
      payAtDepot: false,
      dualControlRemittance: false,
      cancelAfterDelivery: false,
      orderTakerCollects: false,
      onlineOrdering: false,
    },
    vocabulary: {
      depot: 'Cuisine',
      handOver: 'Commandes à servir',
      handOverAction: 'Servir',
      pendingHandOver: 'À servir',
      client: 'Table',
      clientPlaceholder: 'Ex : Table 4',
    },
  },
  {
    type: BusinessType.BAR,
    label: 'Bar',
    icon: '◇',
    description: 'Le bar sert les boissons, le paiement se fait à la caisse.',
    defaults: {
      separateDelivery: true,
      payAtDepot: false,
      dualControlRemittance: false,
      cancelAfterDelivery: false,
      orderTakerCollects: false,
      onlineOrdering: false,
    },
    vocabulary: {
      depot: 'Bar',
      handOver: 'Commandes à servir',
      handOverAction: 'Servir',
      pendingHandOver: 'À servir',
      client: 'Table',
      clientPlaceholder: 'Ex : Table 2 ou Comptoir',
    },
  },
  {
    type: BusinessType.HOTEL,
    label: 'Hôtel-restaurant',
    icon: '⌂',
    description: "Service en salle ou en chambre ; la réception peut encaisser et rapporte l'argent.",
    defaults: {
      separateDelivery: true,
      payAtDepot: false,
      dualControlRemittance: false,
      cancelAfterDelivery: false,
      orderTakerCollects: true,
      onlineOrdering: false,
    },
    vocabulary: {
      depot: 'Service',
      handOver: 'Commandes à servir',
      handOverAction: 'Servir',
      pendingHandOver: 'À servir',
      client: 'Table / chambre',
      clientPlaceholder: 'Ex : Chambre 12',
    },
  },
];

/** Mirrors ShopSettingsService.DEFAULT_TYPE: an installation never configured. */
export const DEFAULT_SHOP_SETTINGS: ShopSettings = {
  businessType: BusinessType.WHOLESALE_DEPOT,
  separateDelivery: true,
  payAtDepot: true,
  dualControlRemittance: true,
  cancelAfterDelivery: false,
  orderTakerCollects: false,
  onlineOrdering: false,
};

export function businessTypeInfo(type: BusinessType): BusinessTypeInfo {
  return BUSINESS_TYPES.find((info) => info.type === type) ?? BUSINESS_TYPES[2];
}

/** Mirrors ShopFeatures.normalized(): what the server will keep of a set of switches. */
export function normalizeFeatures(features: ShopFeatures): ShopFeatures {
  const depotCash = features.separateDelivery && features.payAtDepot;
  return {
    separateDelivery: features.separateDelivery,
    payAtDepot: depotCash,
    dualControlRemittance: hasCashTrail(features) && features.dualControlRemittance,
    cancelAfterDelivery: features.cancelAfterDelivery,
    orderTakerCollects: features.orderTakerCollects,
    onlineOrdering: features.onlineOrdering,
  };
}

/** True when someone other than a cashier takes money and brings it to the till. */
export function hasCashTrail(features: ShopFeatures): boolean {
  return (features.separateDelivery && features.payAtDepot) || features.orderTakerCollects;
}
