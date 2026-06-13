import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type ProgressTone = 'brand' | 'success' | 'warning';

/**
 * Progress — determinate progress bar (onboarding completion, run progress).
 * Mirrors `_ds_bundle.js` → Progress.jsx.
 */
@Component({
  selector: 'hum-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (label() || showValue()) {
      <div
        style="display:flex;justify-content:space-between;margin-bottom:var(--space-1_5);font-size:var(--text-xs);color:var(--text-muted)"
      >
        <span>{{ label() }}</span>
        @if (showValue()) {
          <span class="tabular-nums" style="font-weight:var(--weight-medium);color:var(--text-strong)">{{ rounded() }}%</span>
        }
      </div>
    }
    <div
      class="hum-progress"
      [class]="tone() !== 'brand' ? 'hum-progress--' + tone() : ''"
      role="progressbar"
      [attr.aria-valuenow]="value()"
      [attr.aria-valuemin]="0"
      [attr.aria-valuemax]="max()"
      [attr.aria-label]="label()"
    >
      <div class="hum-progress__bar" [style.width.%]="pct()"></div>
    </div>
  `,
})
export class ProgressComponent {
  readonly value = input(0);
  readonly max = input(100);
  readonly tone = input<ProgressTone>('brand');
  readonly label = input<string>();
  readonly showValue = input(false, { transform: booleanAttribute });

  protected readonly pct = computed(() => Math.min(100, Math.max(0, (this.value() / this.max()) * 100)));
  protected readonly rounded = computed(() => Math.round(this.pct()));
}
