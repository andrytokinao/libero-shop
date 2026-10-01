import { ShortcutMap } from './shortcuts';

function press(key: string, init: KeyboardEventInit = {}): KeyboardEvent {
  return new KeyboardEvent('keydown', { key, cancelable: true, ...init });
}

describe('ShortcutMap', () => {
  let ran: string[];
  let canValidate: boolean;
  let keys: ShortcutMap;

  beforeEach(() => {
    ran = [];
    canValidate = true;
    keys = new ShortcutMap([
      { key: 'F2', label: 'Produit', run: () => ran.push('F2') },
      { key: 'F10', label: 'Valider', run: () => ran.push('F10'), enabled: () => canValidate },
    ]);
  });

  it('runs the command of its key and cancels the browser\'s own use of it', () => {
    const event = press('F2');

    expect(keys.handle(event)).toBeTrue();
    expect(ran).toEqual(['F2']);
    expect(event.defaultPrevented).toBeTrue();
  });

  it('leaves every other key alone — what is typed, or a scanner\'s digits', () => {
    const event = press('5');

    expect(keys.handle(event)).toBeFalse();
    expect(event.defaultPrevented).toBeFalse();
    expect(ran).toEqual([]);
  });

  it('swallows a disabled key without running it', () => {
    canValidate = false;
    const event = press('F10');

    expect(keys.handle(event)).toBeTrue();
    expect(event.defaultPrevented).toBeTrue();
    expect(ran).toEqual([]);
  });

  it('leaves combinations to the browser and the system', () => {
    expect(keys.handle(press('F2', { ctrlKey: true }))).toBeFalse();
    expect(keys.handle(press('F2', { altKey: true }))).toBeFalse();
    expect(ran).toEqual([]);
  });
});
