import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { ThemeService } from 'app/core/theme/theme.service';
import { ButtonComponent, IconButtonComponent } from 'app/shared/ui';

/**
 * `/dev/ui` — living gallery of the Humano primitives. Acts as the visual
 * verification harness: every primitive is rendered against the current theme
 * and, side by side, forced dark so dark-mode parity is always in view.
 * Not a shipped surface — kept under `app/dev`.
 */
@Component({
  selector: 'hum-ui-gallery',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, ButtonComponent, IconButtonComponent],
  template: `
    <div class="ui-gallery">
      <header class="ui-gallery__bar">
        <div>
          <h1 class="text-strong" style="font-size:1.25rem;font-weight:650">Humano · UI gallery</h1>
          <p class="text-soft" style="font-size:.8125rem">Every primitive, light + dark. Verification harness for the design system.</p>
        </div>
        <hum-button variant="secondary" size="sm" [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'" (click)="theme.toggleTheme()">
          {{ theme.theme() === 'dark' ? 'Light' : 'Dark' }}
        </hum-button>
      </header>

      <div class="ui-gallery__panes">
        <section class="ui-pane">
          <div class="ui-pane__tag">Current theme</div>
          <ng-container [ngTemplateOutlet]="swatches" />
        </section>
        <section class="ui-pane" data-theme="dark">
          <div class="ui-pane__tag">Dark</div>
          <ng-container [ngTemplateOutlet]="swatches" />
        </section>
      </div>
    </div>

    <ng-template #swatches>
      <!-- Button -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Button</h2>
        <div class="ui-spec__row">
          <hum-button variant="primary">Primary</hum-button>
          <hum-button variant="secondary">Secondary</hum-button>
          <hum-button variant="outline">Outline</hum-button>
          <hum-button variant="ghost">Ghost</hum-button>
          <hum-button variant="destructive">Destructive</hum-button>
        </div>
        <div class="ui-spec__row">
          <hum-button variant="primary" size="sm">Small</hum-button>
          <hum-button variant="primary">Medium</hum-button>
          <hum-button variant="primary" size="lg">Large</hum-button>
        </div>
        <div class="ui-spec__row">
          <hum-button variant="primary" icon="plus">New employee</hum-button>
          <hum-button variant="secondary" trailingIcon="arrow-right">Next</hum-button>
          <hum-button variant="destructive" [loading]="true">Run payroll</hum-button>
          <hum-button variant="primary" [disabled]="true">Disabled</hum-button>
        </div>
      </article>

      <!-- IconButton -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">IconButton</h2>
        <div class="ui-spec__row">
          <hum-icon-button icon="x" label="Close" variant="ghost" />
          <hum-icon-button icon="bell" label="Notifications" variant="secondary" />
          <hum-icon-button icon="settings" label="Settings" variant="outline" />
          <hum-icon-button icon="trash-2" label="Delete" variant="destructive" />
          <hum-icon-button icon="search" label="Search" variant="ghost" size="sm" />
          <hum-icon-button icon="search" label="Search" variant="ghost" size="lg" />
        </div>
      </article>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: var(--color-app);
    }
    .ui-gallery {
      max-width: 1200px;
      margin: 0 auto;
      padding: 1.5rem;
    }
    .ui-gallery__bar {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    .ui-gallery__panes {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    @media (max-width: 880px) {
      .ui-gallery__panes {
        grid-template-columns: 1fr;
      }
    }
    .ui-pane {
      position: relative;
      padding: 1.5rem 1.25rem 1.25rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg, 12px);
      background: var(--color-app);
      display: grid;
      gap: 1.25rem;
      align-content: start;
    }
    .ui-pane__tag {
      position: absolute;
      top: 0;
      right: 0.75rem;
      transform: translateY(-50%);
      padding: 0.125rem 0.5rem;
      font-size: 0.6875rem;
      font-weight: 600;
      letter-spacing: 0.02em;
      text-transform: uppercase;
      color: var(--color-soft);
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: 999px;
    }
    .ui-spec {
      display: grid;
      gap: 0.75rem;
      padding: 1rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md, 10px);
    }
    .ui-spec__title {
      font-size: 0.75rem;
      font-weight: 650;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--color-soft);
    }
    .ui-spec__row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.625rem;
    }
  `,
})
export default class UiGalleryComponent {
  protected readonly theme = inject(ThemeService);
}
