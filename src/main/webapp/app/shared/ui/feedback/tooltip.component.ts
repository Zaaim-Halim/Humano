import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export type TooltipSide = 'top' | 'bottom';

/**
 * Tooltip — hover/focus label wrapper. Wrap any trigger as projected content;
 * the label shows above (or below). Mirrors `_ds_bundle.js` → Tooltip.jsx.
 */
@Component({
  selector: 'hum-tooltip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    style: 'position: relative; display: inline-flex',
    '(mouseenter)': 'open.set(true)',
    '(mouseleave)': 'open.set(false)',
    '(focusin)': 'open.set(true)',
    '(focusout)': 'open.set(false)',
  },
  template: `
    <ng-content />
    @if (open()) {
      <span class="hum-tooltip" role="tooltip" [style]="positionStyle()">{{ content() }}</span>
    }
  `,
})
export class TooltipComponent {
  readonly content = input.required<string>();
  readonly side = input<TooltipSide>('top');

  protected readonly open = signal(false);
  protected readonly positionStyle = computed(() => {
    const pos = this.side() === 'bottom' ? 'top:calc(100% + 6px)' : 'bottom:calc(100% + 6px)';
    return `position:absolute;left:50%;transform:translateX(-50%);white-space:nowrap;z-index:60;${pos}`;
  });
}
