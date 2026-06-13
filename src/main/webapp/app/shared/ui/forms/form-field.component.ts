import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * FormField — label + control + hint/error wrapper. Wires the label to the
 * control via `for`/`controlId` and paints the error slot (error wins over
 * hint). Project the control as children. Pass already-translated strings.
 * Mirrors `_ds_bundle.js` → FormField.jsx.
 */
@Component({
  selector: 'hum-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-field' },
  template: `
    @if (label(); as l) {
      <label class="hum-field__label" [attr.for]="controlId()">
        {{ l }}
        @if (required()) {
          <span class="hum-field__req" aria-hidden="true">*</span>
        }
      </label>
    }
    <ng-content />
    @if (error(); as e) {
      <span class="hum-field__error" role="alert">{{ e }}</span>
    } @else if (hint(); as h) {
      <span class="hum-field__hint">{{ h }}</span>
    }
  `,
})
export class FormFieldComponent {
  readonly label = input<string>();
  readonly controlId = input<string>();
  readonly required = input(false, { transform: booleanAttribute });
  readonly hint = input<string>();
  readonly error = input<string>();
}
