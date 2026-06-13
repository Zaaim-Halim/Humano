import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

import { ButtonSize, ButtonVariant } from './button.component';

/**
 * IconButton — square, icon-only button. Always pass an accessible `label`
 * (becomes aria-label + title). Shares the `hum-btn` variants/sizes.
 * Mirrors `_ds_bundle.js` → IconButton.jsx.
 *
 * Usage: `<hum-icon-button icon="x" label="Close" variant="ghost" />`
 */
@Component({
  selector: 'hum-icon-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    <button
      type="button"
      class="hum-btn hum-btn--icon"
      [class]="variantClasses()"
      [attr.aria-label]="label()"
      [title]="label()"
      [disabled]="disabled()"
    >
      <lucide-icon [name]="icon()" [size]="iconSize()" />
    </button>
  `,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class IconButtonComponent {
  readonly icon = input.required<string>();
  readonly label = input.required<string>();
  readonly variant = input<ButtonVariant>('ghost');
  readonly size = input<ButtonSize>('md');
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly iconSize = computed(() => {
    switch (this.size()) {
      case 'sm':
        return 14;
      case 'lg':
        return 18;
      default:
        return 16;
    }
  });

  protected readonly variantClasses = computed(() => {
    const size = this.size();
    return [`hum-btn--${this.variant()}`, size !== 'md' ? `hum-btn--${size}` : ''].filter(Boolean).join(' ');
  });
}
