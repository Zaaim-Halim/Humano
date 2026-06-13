import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * PageHeader — standard title + subtitle + actions row for a page body, with an
 * optional breadcrumb slot above. Pass already-translated strings; project a
 * breadcrumb into `[hum-page-breadcrumbs]` and actions into `[hum-page-actions]`.
 * Mirrors `_ds_bundle.js` → PageHeader.jsx.
 */
@Component({
  selector: 'hum-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: block' },
  template: `
    @if (hasBreadcrumbs()) {
      <div style="margin-bottom:var(--space-3)"><ng-content select="[hum-page-breadcrumbs]" /></div>
    }
    <div class="hum-pagehead">
      <div>
        <h1 class="hum-pagehead__title">{{ title() }}</h1>
        @if (subtitle(); as s) {
          <div class="hum-pagehead__sub">{{ s }}</div>
        }
      </div>
      @if (hasActions()) {
        <div class="hum-pagehead__actions"><ng-content select="[hum-page-actions]" /></div>
      }
    </div>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly hasBreadcrumbs = input(false, { transform: booleanAttribute });
  readonly hasActions = input(false, { transform: booleanAttribute });
}
