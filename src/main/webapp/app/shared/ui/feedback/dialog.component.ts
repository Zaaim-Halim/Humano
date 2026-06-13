import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';

/**
 * Dialog — modal for confirmations and small focused forms. Destructive and
 * financial actions must confirm through one of these. Project a footer into
 * `[hum-dialog-footer]`. Mirrors `_ds_bundle.js` → Dialog.jsx.
 */
@Component({
  selector: 'hum-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    style: 'display: contents',
    '(document:keydown.escape)': 'onEscape()',
  },
  template: `
    @if (open()) {
      <div class="hum-overlay" (click)="onOverlayClick($event)">
        <div class="hum-dialog" role="dialog" aria-modal="true" [attr.aria-label]="title()">
          <div class="hum-dialog__header">
            <div class="hum-dialog__title">{{ title() }}</div>
            @if (description(); as d) {
              <div class="hum-dialog__desc">{{ d }}</div>
            }
          </div>
          @if (hasBody()) {
            <div class="hum-dialog__body"><ng-content /></div>
          }
          @if (hasFooter()) {
            <div class="hum-dialog__footer"><ng-content select="[hum-dialog-footer]" /></div>
          }
        </div>
      </div>
    }
  `,
})
export class DialogComponent {
  readonly open = input(false, { transform: booleanAttribute });
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly hasBody = input(true, { transform: booleanAttribute });
  readonly hasFooter = input(false, { transform: booleanAttribute });
  readonly closed = output();

  protected onOverlayClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  protected onEscape(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }
}

/**
 * Drawer — right-hand side panel for detail preview and create/edit without
 * leaving the list. The default "list → detail" affordance. Project a footer
 * into `[hum-drawer-footer]`. Mirrors `_ds_bundle.js` → Drawer.jsx.
 */
@Component({
  selector: 'hum-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    style: 'display: contents',
    '(document:keydown.escape)': 'onEscape()',
  },
  template: `
    @if (open()) {
      <div class="hum-drawer-overlay" (click)="onOverlayClick($event)">
        <div class="hum-drawer" role="dialog" aria-modal="true" [style.width]="width()">
          <div class="hum-drawer__header">
            <div class="hum-card__title">{{ title() }}</div>
            <button
              type="button"
              class="hum-btn hum-btn--ghost hum-btn--icon hum-btn--sm"
              [attr.aria-label]="closeLabel()"
              (click)="closed.emit()"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="hum-drawer__body"><ng-content /></div>
          @if (hasFooter()) {
            <div class="hum-drawer__footer"><ng-content select="[hum-drawer-footer]" /></div>
          }
        </div>
      </div>
    }
  `,
})
export class DrawerComponent {
  readonly open = input(false, { transform: booleanAttribute });
  readonly title = input.required<string>();
  readonly width = input<string>();
  readonly closeLabel = input('Close');
  readonly hasFooter = input(false, { transform: booleanAttribute });
  readonly closed = output();

  protected onOverlayClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  protected onEscape(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }
}
