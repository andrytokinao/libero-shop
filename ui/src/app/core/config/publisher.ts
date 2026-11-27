/**
 * Who publishes Moussa Shop, and how a shop reaches them.
 *
 * <p>Compiled in, like the licence key and for the same reason: read from a configuration
 * file, anyone could put their own name where the customer looks for the publisher's.
 */
export const PUBLISHER = {
  name: 'Kinga Digital',
  phone: '+261 34 19 819 72',
  phoneLink: 'tel:+261341981972',
  email: 'contact@kinga-digital.com',
  emailLink: 'mailto:contact@kinga-digital.com',
  website: 'www.kinga-digital.com',
  websiteLink: 'https://www.kinga-digital.com',
} as const;

/**
 * The support team's line, a WhatsApp number: who a shop calls when it is stuck, and who says
 * where the recovery code of a lost administrator password is to be read.
 */
export const SUPPORT = {
  phone: '+261 34 05 680 13',
  whatsappLink: 'https://wa.me/261340568013',
} as const;
