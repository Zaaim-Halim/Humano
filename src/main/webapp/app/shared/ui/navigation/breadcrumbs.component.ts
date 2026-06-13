import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface Crumb {
  label: string;
  /** Router link (preferred) or external href. */
  link?: string | unknown[];
  href?: string;
}

/**
 * Breadcrumbs — path trail for deep pages. The last item is the current page.
 * Item labels should be already-translated strings.
 * Mirrors `_ds_bundle.js` → Breadcrumbs.jsx.
 */
@Component({
  selector: 'hum-breadcrumbs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  host: { class: 'hum-crumbs', role: 'navigation', '[attr.aria-label]': 'ariaLabel()' },
  template: `
    @for (it of items(); track $index; let i = $index, last = $last) {
      @if (i > 0) {
        <span class="hum-crumbs__sep" aria-hidden="true">/</span>
      }
      @if (last) {
        <span class="hum-crumbs__current" aria-current="page">{{ it.label }}</span>
      } @else if (it.link) {
        <a [routerLink]="it.link">{{ it.label }}</a>
      } @else {
        <a [attr.href]="it.href || '#'">{{ it.label }}</a>
      }
    }
  `,
})
export class BreadcrumbsComponent {
  readonly items = input<Crumb[]>([]);
  readonly ariaLabel = input('Breadcrumb');
}
