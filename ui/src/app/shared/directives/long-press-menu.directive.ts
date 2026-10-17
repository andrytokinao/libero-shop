import { DestroyRef, Directive, ElementRef, inject } from '@angular/core';

/** How long a finger rests on a row before its menu opens — the phone's own long press. */
export const LONG_PRESS_MS = 500;
/** A finger that moves more than this is scrolling, not pressing. */
const MOVE_TOLERANCE_PX = 10;

/**
 * Opens the "⋮" of a row or a card on a long press — the phone's gesture — or a right click: the
 * small "⋮" is hard to hit with a thumb, the whole row is not.
 *
 * <p>It presses the row's own "⋮" button, so the menu is the very same, with the same rights. The
 * tap the long press ends with is swallowed: it must not also open the row.
 */
@Directive({
  selector: '[appLongPressMenu]',
  standalone: true,
  host: {
    '(touchstart)': 'onTouchStart($event)',
    '(touchmove)': 'onTouchMove($event)',
    '(touchend)': 'onTouchEnd($event)',
    '(touchcancel)': 'cancel()',
    '(contextmenu)': 'onContextMenu($event)',
  },
})
export class LongPressMenuDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private timer?: ReturnType<typeof setTimeout>;
  private start: { x: number; y: number } | null = null;
  /** The menu just opened by a press: the click that follows is not a tap on the row. */
  private swallowNextClick = false;

  constructor() {
    // In the capture phase: the row's own buttons — its name — must not see that click either.
    const swallow = (event: MouseEvent) => this.onClick(event);
    this.host.nativeElement.addEventListener('click', swallow, true);
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.timer);
      this.host.nativeElement.removeEventListener('click', swallow, true);
    });
  }

  protected onTouchStart(event: TouchEvent): void {
    const touch = event.touches[0];
    this.start = { x: touch.clientX, y: touch.clientY };
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      if (this.openMenu()) {
        this.swallowNextClick = true;
      }
    }, LONG_PRESS_MS);
  }

  protected onTouchMove(event: TouchEvent): void {
    const touch = event.touches[0];
    if (
      this.start &&
      Math.hypot(touch.clientX - this.start.x, touch.clientY - this.start.y) > MOVE_TOLERANCE_PX
    ) {
      this.cancel();
    }
  }

  /**
   * The finger lifted after a long press must not tap what now lies under it — the menu's panel,
   * which opens from the bottom of a phone's screen. The click it may still send comes at once;
   * past that, nothing is left to swallow.
   */
  protected onTouchEnd(event: TouchEvent): void {
    this.cancel();
    if (this.swallowNextClick) {
      event.preventDefault();
      setTimeout(() => (this.swallowNextClick = false), 400);
    }
  }

  protected cancel(): void {
    clearTimeout(this.timer);
    this.start = null;
  }

  /** A right click, or the long press of a phone that reports it so: the menu, not the browser's. */
  protected onContextMenu(event: MouseEvent): void {
    if (this.trigger()) {
      event.preventDefault();
      this.cancel();
      this.openMenu();
    }
  }

  private onClick(event: MouseEvent): void {
    if (this.swallowNextClick) {
      this.swallowNextClick = false;
      event.preventDefault();
      event.stopPropagation();
    }
  }

  private trigger(): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>('.am-trigger');
  }

  /** Presses the row's "⋮", unless its menu is already open. */
  private openMenu(): boolean {
    const trigger = this.trigger();
    if (!trigger || trigger.getAttribute('aria-expanded') === 'true') {
      return false;
    }
    trigger.click();
    return true;
  }
}
