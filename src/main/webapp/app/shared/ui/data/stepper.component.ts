import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Stepper — horizontal workflow progress (payroll run states, onboarding).
 * `steps` are already-translated labels; `current` is the active index.
 * Mirrors `_ds_bundle.js` → Stepper.jsx.
 */
@Component({
  selector: 'hum-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-stepper' },
  template: `
    @for (label of steps(); track label; let i = $index) {
      <div class="hum-stepper__step" [attr.data-state]="state(i)">
        <span class="hum-stepper__line" aria-hidden="true"></span>
        <span class="hum-stepper__dot">
          @if (state(i) === 'complete') {
            <svg
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="3"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M20 6 9 17l-5-5" />
            </svg>
          } @else {
            {{ i + 1 }}
          }
        </span>
        <span class="hum-stepper__label">{{ label }}</span>
      </div>
    }
  `,
})
export class StepperComponent {
  readonly steps = input<string[]>([]);
  readonly current = input(0);

  protected state(i: number): 'complete' | 'current' | 'upcoming' {
    const current = this.current();
    return i < current ? 'complete' : i === current ? 'current' : 'upcoming';
  }
}
