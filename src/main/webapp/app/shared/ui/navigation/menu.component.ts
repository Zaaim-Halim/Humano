import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

export interface MenuItem {
  id?: string;
  label?: string;
  icon?: string;
  shortcut?: string;
  danger?: boolean;
  separator?: boolean;
  heading?: string;
  active?: boolean;
}

/**
 * Menu — dropdown panel of actions. Render inside a relatively-positioned
 * trigger wrapper; this is the floating panel itself. Item labels/headings
 * should be already-translated strings. Mirrors `_ds_bundle.js` → Menu.jsx.
 */
@Component({
  selector: 'hum-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: { class: 'hum-menu', role: 'menu' },
  template: `
    @for (it of items(); track $index) {
      @if (it.separator) {
        <div class="hum-menu__sep" role="separator"></div>
      } @else if (it.heading) {
        <div class="hum-menu__label">{{ it.heading }}</div>
      } @else {
        <button
          type="button"
          role="menuitem"
          class="hum-menu__item"
          [class.hum-menu__item--danger]="it.danger"
          [attr.data-active]="it.active ? 'true' : null"
          (click)="selected.emit(it.id)"
        >
          @if (it.icon; as name) {
            <span aria-hidden="true" [style.display]="'inline-flex'" [style.color]="it.danger ? 'inherit' : 'var(--text-muted)'">
              <lucide-icon [name]="name" [size]="15" />
            </span>
          }
          {{ it.label }}
          @if (it.shortcut; as s) {
            <kbd>{{ s }}</kbd>
          }
        </button>
      }
    }
  `,
})
export class MenuComponent {
  readonly items = input<MenuItem[]>([]);
  readonly selected = output<string | undefined>();
}
