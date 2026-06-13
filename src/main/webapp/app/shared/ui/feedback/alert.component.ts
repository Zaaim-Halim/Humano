import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type FeedbackTone = 'info' | 'success' | 'warning' | 'danger';

/** Shared tone-icon SVGs, matching `_ds_bundle.js` ALERT_ICONS. */
@Component({
  selector: 'hum-tone-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @switch (tone()) {
        @case ('success') {
          <circle cx="12" cy="12" r="10" />
          <path d="m9 12 2 2 4-4" />
        }
        @case ('warning') {
          <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
          <path d="M12 9v4M12 17h.01" />
        }
        @case ('danger') {
          <circle cx="12" cy="12" r="10" />
          <path d="m15 9-6 6M9 9l6 6" />
        }
        @default {
          <circle cx="12" cy="12" r="10" />
          <path d="M12 16v-4M12 8h.01" />
        }
      }
    </svg>
  `,
})
export class ToneIconComponent {
  readonly tone = input<FeedbackTone>('info');
}

/**
 * Alert — inline contextual message block (stays in the page flow).
 * Mirrors `_ds_bundle.js` → Alert.jsx.
 */
@Component({
  selector: 'hum-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ToneIconComponent],
  host: {
    class: 'hum-alert',
    '[class]': '"hum-alert--" + tone()',
    '[attr.role]': 'tone() === "danger" ? "alert" : "status"',
  },
  template: `
    <span class="hum-alert__icon" aria-hidden="true"><hum-tone-icon [tone]="tone()" /></span>
    <div>
      @if (title(); as t) {
        <div class="hum-alert__title">{{ t }}</div>
      }
      <div class="hum-alert__body"><ng-content /></div>
    </div>
  `,
})
export class AlertComponent {
  readonly tone = input<FeedbackTone>('info');
  readonly title = input<string>();
}

/**
 * Toast — floating notification card. Render inside a toast stack (fixed,
 * bottom-right). Auto-dismiss is the caller's job. Project an action row into
 * `[hum-toast-action]`. Mirrors `_ds_bundle.js` → Toast.jsx.
 */
@Component({
  selector: 'hum-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ToneIconComponent],
  host: { class: 'hum-toast', role: 'status' },
  template: `
    <span class="hum-toast__icon" [style.color]="toneColor()" aria-hidden="true"><hum-tone-icon [tone]="tone()" /></span>
    <div style="flex:1">
      <div class="hum-toast__title">{{ title() }}</div>
      @if (description(); as d) {
        <div class="hum-toast__desc">{{ d }}</div>
      }
      <div style="margin-top:var(--space-2)"><ng-content select="[hum-toast-action]" /></div>
    </div>
    @if (dismissible()) {
      <button type="button" class="hum-toast__close" [attr.aria-label]="dismissLabel()" (click)="closed.emit()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M18 6 6 18M6 6l12 12" />
        </svg>
      </button>
    }
  `,
})
export class ToastComponent {
  readonly tone = input<FeedbackTone>('info');
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly dismissible = input(true);
  readonly dismissLabel = input('Dismiss');
  readonly closed = output();

  protected readonly toneColor = computed(() => `var(--${this.tone()}-solid)`);
}
