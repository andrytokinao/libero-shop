/** Helpers producing ISO-8601 local date-times, the wire format of LocalDateTime. */

/** How far the shop's clock is from this device's, once the server said its time. */
let shopOffsetMs = 0;

/**
 * The shop's time, set from the server's: a phone on the wrong day or in another zone must not
 * count "today" by itself. Called once signed in; until then, the device's own clock.
 *
 * @param serverNow the server's local date-time, `yyyy-MM-ddTHH:mm:ss`
 */
export function setShopClock(serverNow: string): void {
  const server = new Date(serverNow.slice(0, 19)).getTime();
  shopOffsetMs = Number.isFinite(server) ? server - Date.now() : 0;
}

/** The shop's current date and time. */
export function shopNow(): Date {
  return new Date(Date.now() + shopOffsetMs);
}

function toLocalIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

/** A local calendar day as `yyyy-MM-dd`, the wire format of LocalDate. */
export function isoDate(date: Date): string {
  return toLocalIso(date).slice(0, 10);
}

export function nowIso(): string {
  return toLocalIso(shopNow());
}

export function atToday(hours: number, minutes: number): string {
  const date = shopNow();
  date.setHours(hours, minutes, 0, 0);
  return toLocalIso(date);
}

export function daysAgoAt(days: number, hours: number, minutes: number): string {
  const date = shopNow();
  date.setDate(date.getDate() - days);
  date.setHours(hours, minutes, 0, 0);
  return toLocalIso(date);
}

/** True when the ISO date-time falls on the current calendar day. */
export function isToday(isoDateTime: string): boolean {
  return isoDateTime.slice(0, 10) === nowIso().slice(0, 10);
}

/** True when the ISO date-time falls within the last `days` calendar days. */
export function isWithinLastDays(isoDateTime: string, days: number): boolean {
  return isoDateTime.slice(0, 10) >= daysAgoAt(days, 0, 0).slice(0, 10);
}
