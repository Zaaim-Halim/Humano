import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';

/**
 * Tag — removable token for filters, skills, multi-select values. Set
 * `removable` to show the dismiss control and listen to `(removed)`.
 * `removeLabel` is the accessible label (pass a translated string).
 * Mirrors `_ds_bundle.js` → Tag.jsx.
 */
@Component({
  selector: 'hum-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-tag',
    '[class.hum-tag--plain]': '!removable()',
  },
  template: `
    <ng-content />
    @if (removable()) {
      <button type="button" class="hum-tag__x" [attr.aria-label]="removeLabel()" (click)="removed.emit()">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <path d="M18 6 6 18M6 6l12 12" />
        </svg>
      </button>
    }
  `,
})
export class TagComponent {
  readonly removable = input(false, { transform: booleanAttribute });
  readonly removeLabel = input('Remove');
  readonly removed = output();
}
