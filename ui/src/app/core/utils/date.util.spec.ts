import { isoDate, nowIso, setShopClock, shopNow } from './date.util';

describe('date.util — horloge de la boutique', () => {
  afterEach(() => setShopClock(nowLocal(new Date())));

  /** A date as the server writes it: local, without zone. */
  function nowLocal(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return (
      `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
      `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    );
  }

  it('un téléphone en retard d un jour compte « aujourd hui » comme la boutique', () => {
    const shopTomorrow = new Date(Date.now() + 24 * 3600_000);

    setShopClock(nowLocal(shopTomorrow));

    expect(isoDate(shopNow())).toBe(isoDate(shopTomorrow));
    expect(nowIso().slice(0, 10)).toBe(isoDate(shopTomorrow));
  });

  it('une heure illisible laisse l horloge du téléphone', () => {
    setShopClock('pas une date');

    expect(Math.abs(shopNow().getTime() - Date.now())).toBeLessThan(1000);
  });
});
