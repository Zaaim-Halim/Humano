import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  icon?: string;
}

/**
 * Tabs — underline tab strip. Controlled via two-way `value`. Item labels
 * should be already-translated strings. Mirrors `_ds_bundle.js` → Tabs.jsx.
 */
@Component({
  selector: 'hum-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: { class: 'hum-tabs', role: 'tablist' },
  template: `
    @for (it of items(); track it.id) {
      <button
        type="button"
        role="tab"
        [attr.aria-selected]="value() === it.id ? 'true' : 'false'"
        class="hum-tabs__tab"
        (click)="value.set(it.id)"
      >
        @if (it.icon; as name) {
          <span aria-hidden="true" style="display:inline-flex"><lucide-icon [name]="name" [size]="15" /></span>
        }
        {{ it.label }}
        @if (it.count !== undefined && it.count !== null) {
          <span class="hum-tabs__count">{{ it.count }}</span>
        }
      </button>
    }
  `,
})
export class TabsComponent {
  readonly items = input<TabItem[]>([]);
  readonly value = model<string>();
}
