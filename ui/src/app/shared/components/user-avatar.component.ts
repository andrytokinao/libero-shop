import { Component, ElementRef, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { ROLE_LABELS, UserRef } from '../../core/models';
import { UserPhotos } from '../../core/services/user-photos.service';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

/** Long enough that sweeping the mouse across a list opens nothing, short enough to feel immediate. */
const HOVER_DELAY_MS = 350;
const TIP_WIDTH = 240;
const MARGIN = 8;

let nextTipId = 0;

/**
 * A person, the same way on every screen: their photo — or their initials on a colour of their
 * own when they have none — optionally their name beside it, and a card on hover with who they
 * are and what they do.
 *
 * <p>Takes a {@link UserRef}, so a full account, a sale's seller or an order's cash holder all
 * fit. The photo is fetched once per person and version by {@link UserPhotos}, however many rows
 * show them.
 *
 * <p>The card opens on hover after a short delay, at once on keyboard focus, and on a tap on a
 * touch screen; it closes on leaving, on Escape and on scroll. It is placed against the viewport
 * (`position: fixed`), so a table that scrolls sideways or a dialog cannot clip it, and flips
 * above the avatar near the bottom of the screen.
 */
@Component({
  selector: 'app-user-avatar',
  standalone: true,
  host: {
    class: 'user-avatar',
    '[attr.tabindex]': 'tooltip() ? 0 : null',
    '[attr.aria-describedby]': 'open() ? tipId : null',
    '(mouseenter)': 'showSoon()',
    '(mouseleave)': 'hide()',
    '(focus)': 'show()',
    '(blur)': 'hide()',
    '(keydown.escape)': 'hide()',
    '(pointerup)': 'toggleOnTouch($event)',
  },
  template: `
    <span
      class="face"
      [class]="'face s-' + size()"
      [style.background]="photoUrl() ? null : colour()"
      role="img"
      [attr.aria-label]="showName() ? null : user().fullName"
    >
      @if (photoUrl(); as src) {
        <img [src]="src" alt="" />
      } @else {
        {{ initials() }}
      }
    </span>
    @if (showName()) {
      <span class="text">
        <span class="name" [class.off]="user().enabled === false">{{ user().fullName }}</span>
        @if (caption()) {
          <span class="caption">{{ caption() }}</span>
        }
      </span>
    }

    @if (open()) {
      <div
        class="tip"
        role="tooltip"
        [id]="tipId"
        [class.above]="placement().above"
        [style.top.px]="placement().top"
        [style.left.px]="placement().left"
      >
        <span class="face s-xl" [style.background]="photoUrl() ? null : colour()">
          @if (photoUrl(); as src) {
            <img [src]="src" alt="" />
          } @else {
            {{ initials() }}
          }
        </span>
        <div class="tip-text">
          <strong>{{ user().fullName }}</strong>
          @if (user().username) {
            <span class="muted">{{ '@' + user().username }}</span>
          }
          @if (roleNames()) {
            <span class="roles">{{ roleNames() }}</span>
          }
          @if (user().enabled === false) {
            <span class="off">Compte désactivé</span>
          }
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
      vertical-align: middle;
      outline: none;
    }

    :host(:focus-visible) .face {
      box-shadow: 0 0 0 2px var(--panel), 0 0 0 4px var(--brand);
    }

    .face {
      flex: 0 0 auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      overflow: hidden;
      color: #fff;
      font-weight: 700;
      letter-spacing: 0.02em;
      user-select: none;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .s-xs { width: 20px; height: 20px; font-size: 9px; }
    .s-sm { width: 28px; height: 28px; font-size: 11px; }
    .s-md { width: 36px; height: 36px; font-size: 13px; }
    .s-lg { width: 48px; height: 48px; font-size: 17px; }
    .s-xl { width: 72px; height: 72px; font-size: 24px; }

    .text {
      display: flex;
      flex-direction: column;
      min-width: 0;
      line-height: 1.25;
    }

    .name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      /* Greyed, not struck: in a list of sales a departed colleague's name is still a fact, and
         the card says the account is disabled. */
      &.off {
        opacity: 0.6;
      }
    }

    .caption {
      font-size: 11px;
      opacity: 0.75;
    }

    .tip {
      position: fixed;
      z-index: 200;
      width: ${TIP_WIDTH}px;
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 12px;
      border-radius: 12px;
      background: var(--panel, #fff);
      color: var(--ink, #152420);
      border: 1px solid var(--line, #dfe6df);
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
      font-size: 13px;
      font-weight: 400;
      text-align: left;
      pointer-events: none;
      animation: tip-in 120ms ease-out;

      &.above {
        transform: translateY(-100%);
      }
    }

    .tip-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;

      strong {
        font-size: 14px;
      }
    }

    .roles {
      font-size: 12px;
      color: var(--brand-dark, #123625);
    }

    .off {
      font-size: 12px;
      color: var(--red, #a13a2f);
      font-weight: 600;
    }

    @keyframes tip-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @media (prefers-reduced-motion: reduce) {
      .tip { animation: none; }
    }
  `,
})
export class UserAvatarComponent implements OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly photos = inject(UserPhotos);

  readonly user = input.required<UserRef>();
  readonly size = input<AvatarSize>('sm');
  /** The name beside the face; off, the face alone (the name is then its accessible label). */
  readonly showName = input(false);
  /** A second line under the name: a role, "a versé", a time. */
  readonly caption = input<string | null>(null);
  /** The card on hover. Off where the person is already described in full right beside it. */
  readonly tooltip = input(true);

  protected readonly tipId = `user-tip-${nextTipId++}`;
  protected readonly open = signal(false);
  protected readonly placement = signal({ top: 0, left: 0, above: false });

  protected readonly initials = computed(() =>
    this.user()
      .fullName.trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word[0] ?? '')
      .join('')
      .toUpperCase(),
  );

  /** A hue of their own, from their id: the same person has the same colour on every screen. */
  protected readonly colour = computed(() => `hsl(${(this.user().id * 47) % 360} 42% 38%)`);

  private readonly photo = computed(() => {
    const { id, photoVersion } = this.user();
    return photoVersion ? this.photos.url(id, photoVersion) : null;
  });
  protected readonly photoUrl = computed(() => this.photo()?.() ?? null);

  protected readonly roleNames = computed(() =>
    (this.user().roles ?? []).map((role) => ROLE_LABELS[role]).join(' · '),
  );

  private timer?: ReturnType<typeof setTimeout>;
  private readonly onScroll = () => this.hide();

  protected showSoon(): void {
    if (!this.tooltip()) {
      return;
    }
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.show(), HOVER_DELAY_MS);
  }

  protected show(): void {
    if (!this.tooltip() || this.open()) {
      return;
    }
    clearTimeout(this.timer);
    this.placement.set(this.place());
    this.open.set(true);
    // Capture: a scroll inside any container, not only the page, leaves the card behind.
    window.addEventListener('scroll', this.onScroll, true);
  }

  protected hide(): void {
    clearTimeout(this.timer);
    if (this.open()) {
      this.open.set(false);
      window.removeEventListener('scroll', this.onScroll, true);
    }
  }

  /**
   * A touch screen has no hover: a tap opens the card, the next one closes it. On the pointer's
   * release, not its press, so a finger that starts a scroll on an avatar still scrolls.
   */
  protected toggleOnTouch(event: PointerEvent): void {
    if (!this.tooltip() || event.pointerType !== 'touch') {
      return;
    }
    if (this.open()) {
      this.hide();
    } else {
      this.show();
    }
  }

  ngOnDestroy(): void {
    this.hide();
  }

  /** Below the avatar, centred, kept on screen; above it when there is no room below. */
  private place(): { top: number; left: number; above: boolean } {
    const rect = this.host.nativeElement.getBoundingClientRect();
    const left = Math.min(
      Math.max(rect.left + rect.width / 2 - TIP_WIDTH / 2, MARGIN),
      window.innerWidth - TIP_WIDTH - MARGIN,
    );
    const above = rect.bottom + 120 > window.innerHeight;
    return { top: above ? rect.top - MARGIN : rect.bottom + MARGIN, left, above };
  }
}
