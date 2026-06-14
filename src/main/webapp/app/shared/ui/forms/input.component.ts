import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';

/**
 * Input — single-line text input. Supports a leading icon, suffix (e.g. a
 * currency code), invalid state and mono/tabular mode for figures. Implements
 * ControlValueAccessor for reactive/template forms.
 * Mirrors `_ds_bundle.js` → Input.jsx.
 */
@Component({
  selector: 'hum-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, NgTemplateOutlet],
  host: { style: 'display: contents' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => InputComponent), multi: true }],
  template: `
    <ng-template #control>
      <input
        class="hum-input"
        [class.hum-input--mono]="mono()"
        [attr.id]="inputId()"
        [type]="type()"
        [value]="value()"
        [attr.placeholder]="placeholder()"
        [attr.aria-invalid]="invalid() ? 'true' : null"
        [disabled]="isDisabled()"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
    </ng-template>
    @if (icon() || suffix()) {
      <span class="hum-input-wrap">
        @if (icon(); as name) {
          <span class="hum-input-wrap__icon" aria-hidden="true"><lucide-icon [name]="name" [size]="16" /></span>
        }
        <ng-container [ngTemplateOutlet]="control" />
        @if (suffix(); as s) {
          <span class="hum-input-wrap__suffix">{{ s }}</span>
        }
      </span>
    } @else {
      <ng-container [ngTemplateOutlet]="control" />
    }
  `,
})
export class InputComponent implements ControlValueAccessor {
  /** Native input id — set to match a `FormField` `controlId` for label association. */
  readonly inputId = input<string>();
  readonly type = input('text');
  readonly placeholder = input<string>();
  readonly icon = input<string>();
  readonly suffix = input<string>();
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly mono = input(false, { transform: booleanAttribute });
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

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value.set(value);
    this.onChange(value);
  }
}

/**
 * Textarea — multi-line input sharing Input's skin.
 */
@Component({
  selector: 'hum-textarea',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextareaComponent), multi: true }],
  template: `
    <textarea
      class="hum-textarea"
      [rows]="rows()"
      [value]="value()"
      [attr.placeholder]="placeholder()"
      [attr.aria-invalid]="invalid() ? 'true' : null"
      [disabled]="isDisabled()"
      (input)="onInput($event)"
      (blur)="onTouched()"
    ></textarea>
  `,
})
export class TextareaComponent implements ControlValueAccessor {
  readonly placeholder = input<string>();
  readonly rows = input(4);
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

  protected onInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.value.set(value);
    this.onChange(value);
  }
}
