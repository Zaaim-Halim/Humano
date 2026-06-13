import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * Select — native select with the Humano skin (custom chevron, focus ring).
 * Provide `options` or project `<option>` children. Implements
 * ControlValueAccessor. Mirrors `_ds_bundle.js` → Select.jsx.
 */
@Component({
  selector: 'hum-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SelectComponent), multi: true }],
  template: `
    <select
      class="hum-select"
      [value]="value()"
      [attr.aria-invalid]="invalid() ? 'true' : null"
      [disabled]="isDisabled()"
      (change)="onSelect($event)"
      (blur)="onTouched()"
    >
      @if (options(); as opts) {
        @for (o of opts; track o.value) {
          <option [value]="o.value">{{ o.label }}</option>
        }
      } @else {
        <ng-content />
      }
    </select>
  `,
})
export class SelectComponent implements ControlValueAccessor {
  readonly options = input<SelectOption[]>();
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly value = signal('');
  protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());
  private readonly disabledByForm = signal(false);

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledByForm.set(isDisabled);
  }

  protected onChange: (value: string) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  protected onSelect(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.value.set(value);
    this.onChange(value);
  }
}
