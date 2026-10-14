import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface TrendPoint {
  /** Axis label under the point (e.g. "2024", "Jun 25"). */
  label: string;
  value: number;
  /** Tooltip text; defaults to "label: value". */
  title?: string;
  /** Text drawn above the point (e.g. a formatted amount or "+5%"). */
  annotation?: string;
}

/**
 * Trend chart — a small SVG line (or step) chart with an area fill, a dot per point, the
 * labels underneath and an optional annotation above each point. It scales to its container's
 * width; `min`/`max` fix the value range (otherwise it fits the data with some headroom).
 * Used for salary progression (step) and review ratings (line).
 */
@Component({
  selector: 'hum-trend-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-trend', '[attr.aria-label]': 'label()', role: 'img' },
  template: `
    <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" class="hum-trend__svg" aria-hidden="true">
      @for (g of chart().grid; track $index) {
        <line class="hum-trend__grid" [attr.x1]="PAD_X" [attr.x2]="W - PAD_X" [attr.y1]="g" [attr.y2]="g" />
      }
      <path class="hum-trend__area" [attr.d]="chart().area" />
      <path class="hum-trend__line" [attr.d]="chart().line" />
    </svg>
    <!-- Dots and text as HTML so they don't stretch with the SVG. -->
    @for (p of chart().points; track $index; let last = $last) {
      <div class="hum-trend__point" [class.is-last]="last" [style.left.%]="p.x" [style.top.%]="p.y" [attr.title]="p.title">
        @if (p.annotation) {
          <span class="hum-trend__annotation">{{ p.annotation }}</span>
        }
      </div>
      <span class="hum-trend__label" [style.left.%]="p.x">{{ p.label }}</span>
    }
  `,
})
export class TrendChartComponent {
  readonly points = input.required<TrendPoint[]>();
  readonly label = input('');
  readonly step = input(false);
  readonly min = input<number | null>(null);
  readonly max = input<number | null>(null);

  protected readonly W = 600;
  protected readonly H = 160;
  protected readonly PAD_X = 24;
  private readonly PAD_TOP = 28;
  private readonly PAD_BOTTOM = 8;

  protected readonly chart = computed(() => {
    const pts = this.points();
    const values = pts.map(p => p.value);
    const lo0 = this.min() ?? Math.min(...values);
    const hi0 = this.max() ?? Math.max(...values);
    const pad = this.min() === null && this.max() === null ? (hi0 - lo0 || hi0 || 1) * 0.15 : 0;
    const lo = this.min() ?? Math.max(0, lo0 - pad);
    const hi = this.max() ?? hi0 + pad;
    const span = hi - lo || 1;
    const innerW = this.W - this.PAD_X * 2;
    const innerH = this.H - this.PAD_TOP - this.PAD_BOTTOM;
    const xy = pts.map((p, i) => ({
      x: this.PAD_X + (pts.length === 1 ? innerW / 2 : (innerW * i) / (pts.length - 1)),
      y: this.PAD_TOP + innerH * (1 - (p.value - lo) / span),
    }));
    let line = '';
    xy.forEach((p, i) => {
      if (i === 0) line = `M${p.x},${p.y}`;
      else if (this.step()) line += ` H${p.x} V${p.y}`;
      else line += ` L${p.x},${p.y}`;
    });
    const bottom = this.H - this.PAD_BOTTOM;
    const area = xy.length ? `${line} V${bottom} H${xy[0].x} Z` : '';
    return {
      line,
      area,
      grid: [0, 0.5, 1].map(f => this.PAD_TOP + innerH * f),
      points: pts.map((p, i) => ({
        ...p,
        x: (xy[i].x / this.W) * 100,
        y: (xy[i].y / this.H) * 100,
        title: p.title ?? `${p.label}: ${p.value}`,
      })),
    };
  });
}
