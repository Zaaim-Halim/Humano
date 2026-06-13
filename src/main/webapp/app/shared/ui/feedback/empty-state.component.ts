import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

/**
 * EmptyState — empty list/section placeholder with icon, copy and a clear
 * primary action. Project the action into `[hum-empty-action]`.
 * Mirrors `_ds_bundle.js` → EmptyState.jsx.
 */
@Component({
  selector: 'hum-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: { class: 'hum-empty' },
  template: `
    @if (icon(); as name) {
      <span class="hum-empty__icon" aria-hidden="true"><lucide-icon [name]="name" [size]="28" /></span>
    }
    <div class="hum-empty__title">{{ title() }}</div>
    @if (description(); as d) {
      <div class="hum-empty__desc">{{ d }}</div>
    }
    @if (hasAction()) {
      <div style="margin-top:var(--space-1)"><ng-content select="[hum-empty-action]" /></div>
    }
  `,
})
export class EmptyStateComponent {
  readonly icon = input<string>();
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly hasAction = input(false, { transform: booleanAttribute });
}

/**
 * Skeleton — shimmer placeholder. Compose rows for loading states; never show
 * spinner-only screens. Mirrors `_ds_bundle.js` → Skeleton.jsx.
 */
@Component({
  selector: 'hum-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'hum-skeleton',
    'aria-hidden': 'true',
    style: 'display: block',
    '[style.width]': 'width()',
    '[style.height.px]': 'height()',
    '[style.border-radius]': 'circle() ? "50%" : null',
  },
  template: ``,
})
export class SkeletonComponent {
  readonly width = input<string>('100%');
  readonly height = input(14);
  readonly circle = input(false, { transform: booleanAttribute });
}

/**
 * SkeletonRow — avatar + two lines, the standard list-loading row.
 */
@Component({
  selector: 'hum-skeleton-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent],
  template: `
    <div style="display:flex;align-items:center;gap:var(--space-3);padding:var(--space-2_5) 0">
      <hum-skeleton width="32px" [height]="32" [circle]="true" />
      <div style="flex:1;display:flex;flex-direction:column;gap:6px">
        <hum-skeleton width="38%" [height]="11" />
        <hum-skeleton width="22%" [height]="9" />
      </div>
      <hum-skeleton width="64px" [height]="20" style="border-radius:var(--radius-full)" />
    </div>
  `,
})
export class SkeletonRowComponent {}
