import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, model, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Switch — on/off toggle for instant-effect settings. Optional label (pass a
 * translated string). Implements ControlValueAccessor (boolean).
 * Mirrors `_ds_bundle.js` → Switch.jsx.
 */
@Component({
  selector: 'hum-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SwitchComponent), multi: true }],
  template: `
    <label class="hum-switch">
      <input
        type="checkbox"
        role="switch"
        [checked]="checked()"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel()"
        (change)="onToggle($event)"
        (blur)="onTouched()"
      />
      <span class="hum-switch__track" aria-hidden="true"><span class="hum-switch__thumb"></span></span>
      @if (label(); as l) {
        <span style="font-size:var(--text-base);color:var(--text-default)">{{ l }}</span>
      }
    </label>
  `,
})
export class SwitchComponent implements ControlValueAccessor {
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
