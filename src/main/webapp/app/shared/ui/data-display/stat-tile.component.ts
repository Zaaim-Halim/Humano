import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

export type TrendDirection = 'up' | 'down' | 'flat';
export type SparklineTone = 'brand' | 'success' | 'danger';

/**
 * StatTile — KPI tile with label, big tabular figure and optional trend.
 * Project a `<hum-sparkline>` (or anything) as children to render below.
 * Mirrors `_ds_bundle.js` → StatTile.jsx.
 */
@Component({
  selector: 'hum-stat-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: { class: 'hum-stat' },
  template: `
    <div class="hum-stat__label">
      @if (icon(); as name) {
        <span aria-hidden="true" style="display:inline-flex;color:var(--text-subtle)"><lucide-icon [name]="name" [size]="14" /></span>
      }
      {{ label() }}
    </div>
    <div class="hum-stat__value">{{ value() }}</div>
    @if (trend(); as t) {
      <div class="hum-stat__trend" [class]="dirClass()">
        <span aria-hidden="true">{{ arrow() }}</span> {{ t }}
      </div>
    }
    <ng-content />
  `,
})
export class StatTileComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly trend = input<string>();
  readonly trendDirection = input<TrendDirection>('flat');
  readonly icon = input<string>();

  protected readonly dirClass = computed(() => `hum-stat__trend--${this.trendDirection()}`);
  protected readonly arrow = computed(() => {
    switch (this.trendDirection()) {
      case 'up':
        return '↑';
      case 'down':
        return '↓';
      default:
        return '→';
    }
  });
}

/**
 * Sparkline — tiny inline trend line for KPI tiles. Pure SVG, no deps.
 */
@Component({
  selector: 'hum-sparkline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data().length) {
      <svg
        [attr.width]="width()"
        [attr.height]="height()"
        [attr.viewBox]="'0 0 ' + width() + ' ' + height()"
        aria-hidden="true"
        style="display:block;margin-top:var(--space-2)"
      >
        <polyline
          [attr.points]="points()"
          fill="none"
          [attr.stroke]="color()"
          stroke-width="1.5"
          stroke-linejoin="round"
          stroke-linecap="round"
        />
      </svg>
    }
  `,
})
export class SparklineComponent {
  readonly data = input<number[]>([]);
  readonly width = input(120);
  readonly height = input(32);
  readonly tone = input<SparklineTone>('brand');

  protected readonly color = computed(() => {
    switch (this.tone()) {
      case 'success':
        return 'var(--success-solid)';
      case 'danger':
        return 'var(--danger-solid)';
      default:
        return 'var(--brand)';
    }
  });

  protected readonly points = computed(() => {
    const data = this.data();
    const w = this.width();
    const h = this.height();
    const min = Math.min(...data);
    const max = Math.max(...data);
    const range = max - min || 1;
    return data.map((v, i) => `${(i / (data.length - 1)) * w},${h - 3 - ((v - min) / range) * (h - 6)}`).join(' ');
  });
}
