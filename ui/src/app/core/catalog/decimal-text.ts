/** "3,5", "3.5", "1 750" → a number; anything else, an empty field included → null. */
export function parseDecimal(value: string): number | null {
  const cleaned = value.replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }
  return Number(cleaned);
}

/** 3.5 → "3,5", 1000000 → "1 000 000": at most three decimals, as quantities are kept. */
export function formatDecimal(value: number): string {
  return value.toLocaleString('fr-FR', { maximumFractionDigits: 3 });
}
