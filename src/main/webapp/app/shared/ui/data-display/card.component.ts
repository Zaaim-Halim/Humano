import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * Card — bordered surface with optional header/footer. The base container for
 * dashboard modules, settings sections and detail panels.
 * Mirrors `_ds_bundle.js` → Card.jsx.
 *
 * Header renders when `title` is set; project actions into `[hum-card-actions]`
 * and a footer into `[hum-card-footer]`. Body is the default slot.
 */
@Component({
  selector: 'hum-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-card',
    '[class.hum-card--flat]': 'flat()',
    '[class.hum-card--interactive]': 'interactive()',
  },
  template: `
    @if (title() || hasHeader()) {
      <div class="hum-card__header">
        @if (title(); as t) {
          <span class="hum-card__title">{{ t }}</span>
        }
        <div style="display:flex;gap:var(--space-2)"><ng-content select="[hum-card-actions]" /></div>
      </div>
    }
    @if (padded()) {
      <div class="hum-card__body"><ng-content /></div>
    } @else {
      <ng-content />
    }
    @if (hasFooter()) {
      <div class="hum-card__footer"><ng-content select="[hum-card-footer]" /></div>
    }
  `,
})
export class CardComponent {
  readonly title = input<string>();
  readonly flat = input(false, { transform: booleanAttribute });
  readonly interactive = input(false, { transform: booleanAttribute });
  readonly padded = input(true, { transform: booleanAttribute });
  /** Force-render the header row even without a `title` (e.g. actions only). */
  readonly hasHeader = input(false, { transform: booleanAttribute });
  /** Render the footer slot. */
  readonly hasFooter = input(false, { transform: booleanAttribute });
}
