/** Helpers producing ISO-8601 local date-times, the wire format of LocalDateTime. */

function toLocalIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

export function nowIso(): string {
  return toLocalIso(new Date());
}

export function atToday(hours: number, minutes: number): string {
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return toLocalIso(date);
}

export function daysAgoAt(days: number, hours: number, minutes: number): string {
  const date = new Date();
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
