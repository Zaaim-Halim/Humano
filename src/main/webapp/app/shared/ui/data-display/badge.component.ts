import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

import { BadgeTone, STATUS_MAP } from 'app/config/status-map';

/**
 * Badge — status pill. Pass a backend enum via `status` (resolved through
 * STATUS_MAP for tone + label) or set `tone` + `label`/projected content.
 * Mirrors `_ds_bundle.js` → Badge.jsx.
 */
@Component({
  selector: 'hum-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-badge',
    '[class]': 'modifierClasses()',
  },
  template: `
    @if (dot()) {
      <span class="hum-badge__dot" aria-hidden="true"></span>
    }
    @if (text(); as t) {
      {{ t }}
    } @else {
      <ng-content />
    }
  `,
})
export class BadgeComponent {
  /** Backend status enum, resolved through STATUS_MAP. */
  readonly status = input<string>();
  readonly tone = input<BadgeTone>('neutral');
  readonly solid = input(false, { transform: booleanAttribute });
  readonly dot = input(true, { transform: booleanAttribute });
  readonly square = input(false, { transform: booleanAttribute });
  /** Explicit label; defaults to the STATUS_MAP label when `status` is set. */
  readonly label = input<string>();

  protected readonly resolvedTone = computed(() => this.mapped()?.tone ?? this.tone());
  protected readonly text = computed(() => this.label() ?? this.mapped()?.label ?? this.status());
  protected readonly modifierClasses = computed(() => {
    const tone = this.resolvedTone();
    return [
      tone !== 'neutral' ? `hum-badge--${tone}` : '',
      this.solid() ? 'hum-badge--solid' : '',
      this.square() ? 'hum-badge--square' : '',
    ]
      .filter(Boolean)
      .join(' ');
  });

  private readonly mapped = computed(() => {
    const s = this.status();
    return s ? STATUS_MAP[s] : null;
  });
}
