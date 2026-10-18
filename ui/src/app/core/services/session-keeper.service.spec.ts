import { ACTIVE_WITHIN_MS, RENEW_WITHIN_MS, WARN_WITHIN_MS, sessionAdvice } from './session-keeper.service';

describe('sessionAdvice', () => {
  const now = Date.parse('2026-10-18T10:00:00Z');
  const minutes = (n: number) => n * 60_000;

  it('session en usage, à moins d une heure de sa fin : prolongée sans un mot', () => {
    expect(sessionAdvice(now, now + minutes(40), now - minutes(2))).toBe('renew');
  });

  it('session en usage mais loin de sa fin : rien', () => {
    expect(sessionAdvice(now, now + RENEW_WITHIN_MS + minutes(30), now)).toBe('none');
  });

  it('session laissée sans usage : rien jusqu aux cinq dernières minutes, puis l avertissement', () => {
    const idle = now - ACTIVE_WITHIN_MS - minutes(1);
    expect(sessionAdvice(now, now + minutes(20), idle)).toBe('none');
    expect(sessionAdvice(now, now + WARN_WITHIN_MS - minutes(1), idle)).toBe('warn');
  });

  it('sans session, ou déjà finie : rien', () => {
    expect(sessionAdvice(now, null, now)).toBe('none');
    expect(sessionAdvice(now, now - 1, now)).toBe('none');
  });
});
