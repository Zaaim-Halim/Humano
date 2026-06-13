import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Checkbox — custom-skinned checkbox with optional label (pass a translated
 * string). Implements ControlValueAccessor (boolean).
 * Mirrors `_ds_bundle.js` → Checkbox.jsx.
 */
@Component({
  selector: 'hum-checkbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CheckboxComponent), multi: true }],
  template: `
    <label class="hum-check">
      <input
        type="checkbox"
        [checked]="checked()"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel()"
        (change)="onToggle($event)"
        (blur)="onTouched()"
      />
      <span class="hum-check__box" aria-hidden="true">
        <svg
          class="hum-check__check"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="3.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M20 6 9 17l-5-5" />
        </svg>
      </span>
      @if (label(); as l) {
        <span>{{ l }}</span>
      }
    </label>
  `,
})
export class CheckboxComponent implements ControlValueAccessor {
  readonly label = input<string>();
  readonly ariaLabel = input<string>();
  readonly checked = model(false);
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());
  private readonly disabledByForm = signal(false);

  writeValue(value: boolean | null): void {
    this.checked.set(!!value);
  }
  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledByForm.set(isDisabled);
  }

  protected onChange: (value: boolean) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  protected onToggle(event: Event): void {
    const value = (event.target as HTMLInputElement).checked;
    this.checked.set(value);
    this.onChange(value);
  }
}

/**
 * Radio — custom-skinned radio button with optional label. Controlled via
 * `checked` + `(selected)`; pair several under one `name`.
 * Mirrors `_ds_bundle.js` → Radio.jsx.
 */
@Component({
  selector: 'hum-radio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  template: `
    <label class="hum-check">
      <input
        type="radio"
        [name]="name()"
        [value]="value()"
        [checked]="checked()"
        [disabled]="disabled()"
        (change)="selected.emit(value())"
      />
      <span class="hum-check__box hum-check__box--radio" aria-hidden="true"></span>
      @if (label(); as l) {
        <span>{{ l }}</span>
      }
    </label>
  `,
})
export class RadioComponent {
  readonly label = input<string>();
  readonly name = input.required<string>();
  readonly value = input.required<string>();
  readonly checked = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly selected = output<string>();
}
