import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Button — primary action control. Renders a native `<button>` carrying the
 * `hum-btn` skin so it keeps button semantics, native `disabled` and `type`.
 * The host is `display: contents`, so the inner button participates directly in
 * the parent's flex/grid layout. Mirrors `_ds_bundle.js` → Button.jsx.
 *
 * Usage: `<hum-button variant="primary" icon="plus">New employee</hum-button>`
 */
@Component({
  selector: 'hum-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    <button
      [type]="type()"
      class="hum-btn"
      [class]="variantClasses()"
      [attr.data-loading]="loading() ? 'true' : null"
      [attr.aria-busy]="loading() ? 'true' : null"
      [disabled]="disabled() || loading()"
    >
      @if (icon(); as name) {
        <span class="hum-btn__icon" aria-hidden="true"><lucide-icon [name]="name" [size]="iconSize()" /></span>
      }
      <ng-content />
      @if (trailingIcon(); as name) {
        <span class="hum-btn__icon" aria-hidden="true"><lucide-icon [name]="name" [size]="iconSize()" /></span>
      }
    </button>
  `,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly block = input(false, { transform: booleanAttribute });
  /** Leading Lucide icon name. */
  readonly icon = input<string>();
  /** Trailing Lucide icon name. */
  readonly trailingIcon = input<string>();

  protected readonly iconSize = computed(() => (this.size() === 'lg' ? 18 : 16));

  protected readonly variantClasses = computed(() => {
    const size = this.size();
    return [`hum-btn--${this.variant()}`, size !== 'md' ? `hum-btn--${size}` : '', this.block() ? 'hum-btn--block' : '']
      .filter(Boolean)
      .join(' ');
  });
}
