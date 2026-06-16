import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { ThemeService } from 'app/core/theme/theme.service';
import {
  AlertComponent,
  AvatarComponent,
  AvatarGroupComponent,
  BadgeComponent,
  BreadcrumbsComponent,
  ButtonComponent,
  CardComponent,
  CheckboxComponent,
  Column,
  Command,
  CommandPaletteComponent,
  Crumb,
  DataTableComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  MenuComponent,
  MenuItem,
  Person,
  ProgressComponent,
  RadioComponent,
  Row,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  SparklineComponent,
  StatTileComponent,
  StepperComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  TagComponent,
  TextareaComponent,
  ToastService,
  TooltipComponent,
} from 'app/shared/ui';

/**
 * `/dev/ui` — living gallery of the Humano primitives. Acts as the visual
 * verification harness: every primitive is rendered against the current theme
 * and, side by side, forced dark so dark-mode parity is always in view.
 *
 * Overlays (Dialog, Drawer), the Toast host and hover-only Tooltip can't use
 * the side-by-side trick — they render in the global `data-theme`. Verify those
 * via the top-bar Dark toggle, not the two-pane comparison.
 *
 * Not a shipped surface — kept under `app/dev`.
 */
@Component({
  selector: 'hum-ui-gallery',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    ButtonComponent,
    IconButtonComponent,
    CommandPaletteComponent,
    BadgeComponent,
    TagComponent,
    AvatarComponent,
    AvatarGroupComponent,
    CardComponent,
    ProgressComponent,
    StatTileComponent,
    SparklineComponent,
    AlertComponent,
    TabsComponent,
    BreadcrumbsComponent,
    MenuComponent,
    InputComponent,
    TextareaComponent,
    SelectComponent,
    CheckboxComponent,
    RadioComponent,
    SwitchComponent,
    FormFieldComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    TooltipComponent,
    StepperComponent,
    DataTableComponent,
    DialogComponent,
    DrawerComponent,
  ],
  template: `
    <div class="ui-gallery">
      <header class="ui-gallery__bar">
        <div>
          <h1 class="text-strong" style="font-size:1.25rem;font-weight:650">Humano · UI gallery</h1>
          <p class="text-soft" style="font-size:.8125rem">Every primitive, light + dark. Verification harness for the design system.</p>
        </div>
        <div style="display:flex;gap:.5rem;align-items:center">
          <hum-button variant="outline" size="sm" icon="search" (click)="palette.openPalette()">Command palette (⌘K)</hum-button>
          <hum-button variant="secondary" size="sm" [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'" (click)="theme.toggleTheme()">
            {{ theme.theme() === 'dark' ? 'Light' : 'Dark' }}
          </hum-button>
        </div>
      </header>

      <hum-command-palette #palette placeholder="Search commands, pages, people…" [commands]="commands" (run)="lastCommand.set($event)" />

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

    <!-- Overlays render once in the global theme (not the two-pane comparison). -->
    <hum-dialog [open]="dialogOpen()" title="Run payroll for June 2026?" [hasFooter]="true" (closed)="dialogOpen.set(false)">
      <p class="text-soft" style="font-size:.875rem">This calculates net pay for 248 employees. You can still review before posting.</p>
      <div hum-dialog-footer style="display:flex;gap:.5rem;justify-content:flex-end">
        <hum-button variant="ghost" (click)="dialogOpen.set(false)">Cancel</hum-button>
        <hum-button variant="primary" (click)="dialogOpen.set(false)">Run payroll</hum-button>
      </div>
    </hum-dialog>

    <hum-drawer [open]="drawerOpen()" title="Employee detail" (closed)="drawerOpen.set(false)">
      <div style="display:flex;align-items:center;gap:.75rem;margin-bottom:1rem">
        <hum-avatar name="Amina Cherkaoui" size="lg" />
        <div>
          <div class="text-strong" style="font-weight:600">Amina Cherkaoui</div>
          <div class="text-soft" style="font-size:.8125rem">Senior Engineer · Engineering</div>
        </div>
      </div>
      <hum-badge status="ACTIVE" />
    </hum-drawer>

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

      <!-- Badge -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Badge</h2>
        <div class="ui-spec__row">
          <hum-badge status="ACTIVE" />
          <hum-badge status="PENDING" />
          <hum-badge status="REJECTED" />
          <hum-badge status="IN_PROGRESS" />
          <hum-badge status="DRAFT" />
        </div>
        <div class="ui-spec__row">
          <hum-badge tone="brand" label="Brand" />
          <hum-badge tone="success" [solid]="true" label="Solid" />
          <hum-badge tone="warning" [dot]="false" label="No dot" />
          <hum-badge tone="info" [square]="true" label="Square" />
        </div>
      </article>

      <!-- Tag -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Tag</h2>
        <div class="ui-spec__row">
          <hum-tag>Engineering</hum-tag>
          <hum-tag>Remote</hum-tag>
          <hum-tag [removable]="true" removeLabel="Remove filter">Status: Active</hum-tag>
        </div>
      </article>

      <!-- Avatar -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Avatar</h2>
        <div class="ui-spec__row">
          <hum-avatar name="Amina Cherkaoui" size="xs" />
          <hum-avatar name="Youssef Bennani" size="sm" />
          <hum-avatar name="Sara El Amrani" size="md" />
          <hum-avatar name="Karim Idrissi" size="lg" />
          <hum-avatar name="Nadia Tazi" size="xl" />
          <hum-avatar name="Omar Fassi" [square]="true" />
        </div>
        <div class="ui-spec__row">
          <hum-avatar-group [people]="people" [max]="4" />
        </div>
      </article>

      <!-- Card -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Card</h2>
        <div class="ui-spec__grid">
          <hum-card title="Plain card">
            <p class="text-soft" style="font-size:.8125rem">Default padded card with a title and body copy.</p>
          </hum-card>
          <hum-card [hasHeader]="true" [hasFooter]="true">
            <div hum-card-header>
              <div class="hum-card__title">With header</div>
            </div>
            <span hum-card-actions><hum-icon-button icon="pencil" label="Edit" variant="ghost" size="sm" /></span>
            <p class="text-soft" style="font-size:.8125rem">Header actions slot + footer.</p>
            <div hum-card-footer><hum-button variant="ghost" size="sm">View</hum-button></div>
          </hum-card>
          <hum-card [interactive]="true" [flat]="true" title="Interactive · flat">
            <p class="text-soft" style="font-size:.8125rem">Hover affordance, no shadow.</p>
          </hum-card>
        </div>
      </article>

      <!-- StatTile + Sparkline -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">StatTile · Sparkline</h2>
        <div class="ui-spec__grid">
          <hum-stat-tile label="Headcount" [value]="248" trend="+12 this quarter" trendDirection="up" icon="users">
            <hum-sparkline [data]="sparkData" tone="success" />
          </hum-stat-tile>
          <hum-stat-tile label="On leave today" [value]="7" trend="-2 vs yesterday" trendDirection="down" icon="palmtree" />
          <hum-stat-tile label="Pending approvals" [value]="17" trend="No change" trendDirection="flat" icon="check-check" />
        </div>
      </article>

      <!-- Progress -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Progress</h2>
        <div style="display:grid;gap:.625rem">
          <hum-progress [value]="72" tone="brand" [showValue]="true" />
          <hum-progress [value]="40" tone="success" />
          <hum-progress [value]="88" tone="warning" [showValue]="true" />
        </div>
      </article>

      <!-- Alert -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Alert</h2>
        <div style="display:grid;gap:.625rem">
          <hum-alert tone="info" title="Heads up">Payroll closes in 2 days.</hum-alert>
          <hum-alert tone="success" title="Posted">June payroll posted to 248 employees.</hum-alert>
          <hum-alert tone="warning" title="Review needed">3 timesheets are missing approvals.</hum-alert>
          <hum-alert tone="danger" title="Failed">Could not reach the payment gateway.</hum-alert>
        </div>
      </article>

      <!-- Tabs -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Tabs</h2>
        <hum-tabs [items]="tabs" [(value)]="tabValue" />
        <p class="text-soft" style="font-size:.8125rem">Active: {{ tabValue() }}</p>
      </article>

      <!-- Breadcrumbs -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Breadcrumbs</h2>
        <hum-breadcrumbs [items]="crumbs" />
      </article>

      <!-- Menu -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Menu</h2>
        <div class="ui-menu-anchor">
          <hum-menu [items]="menuItems" (selected)="lastCommand.set('menu:' + $event)" />
        </div>
      </article>

      <!-- Forms -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Forms</h2>
        <div class="ui-spec__grid">
          <hum-form-field label="Full name" controlId="g-name" [required]="true" hint="As on the contract">
            <hum-input inputId="g-name" placeholder="Amina Cherkaoui" />
          </hum-form-field>
          <hum-form-field label="Work email" controlId="g-email" error="Enter a valid email">
            <hum-input inputId="g-email" type="email" placeholder="name@humano.io" [invalid]="true" />
          </hum-form-field>
          <hum-form-field label="Department" controlId="g-dept">
            <hum-select [options]="selectOptions" />
          </hum-form-field>
          <hum-form-field label="Disabled" controlId="g-dis">
            <hum-input inputId="g-dis" placeholder="Read only" [disabled]="true" />
          </hum-form-field>
        </div>
        <hum-form-field label="Notes" controlId="g-notes">
          <hum-textarea [rows]="3" placeholder="Add context for the approver…" />
        </hum-form-field>
        <div class="ui-spec__row">
          <hum-checkbox label="Email me on approval" [(checked)]="checkboxOn" />
          <hum-checkbox label="Disabled" [disabled]="true" />
          <hum-switch label="Auto-post payroll" [(checked)]="switchOn" />
        </div>
        <div class="ui-spec__row">
          <hum-radio
            label="Weekly"
            name="g-cadence"
            value="weekly"
            [checked]="radioValue() === 'weekly'"
            (selected)="radioValue.set($event)"
          />
          <hum-radio
            label="Monthly"
            name="g-cadence"
            value="monthly"
            [checked]="radioValue() === 'monthly'"
            (selected)="radioValue.set($event)"
          />
        </div>
      </article>

      <!-- EmptyState -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">EmptyState</h2>
        <hum-empty-state
          icon="check-check"
          title="No approvals pending"
          description="When a request needs your sign-off it shows up here."
          [hasAction]="true"
        >
          <hum-button hum-empty-action variant="primary" size="sm" icon="plus">New request</hum-button>
        </hum-empty-state>
      </article>

      <!-- Skeleton -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Skeleton</h2>
        <div style="display:grid;gap:.5rem">
          <hum-skeleton width="60%" [height]="14" />
          <hum-skeleton width="40%" [height]="14" />
          <hum-skeleton-row />
          <hum-skeleton-row />
        </div>
      </article>

      <!-- Tooltip -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Tooltip</h2>
        <div class="ui-spec__row">
          <hum-tooltip content="Shows above on hover/focus">
            <hum-button variant="outline" size="sm">Hover me (top)</hum-button>
          </hum-tooltip>
          <hum-tooltip content="Shows below on hover/focus" side="bottom">
            <hum-button variant="outline" size="sm">Hover me (bottom)</hum-button>
          </hum-tooltip>
        </div>
      </article>

      <!-- Stepper -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Stepper</h2>
        <hum-stepper [steps]="['Draft', 'Calculated', 'Approved', 'Posted']" [current]="2" />
      </article>

      <!-- DataTable -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">DataTable</h2>
        <hum-data-table
          [columns]="tableColumns"
          [rows]="tableRows"
          [selectable]="true"
          [clickableRows]="true"
          (rowClick)="lastCommand.set('row:' + $any($event).name)"
        />
      </article>

      <!-- Overlays + Toast (render in the global theme) -->
      <article class="ui-spec">
        <h2 class="ui-spec__title">Overlays · Toast</h2>
        <div class="ui-spec__row">
          <hum-button variant="primary" (click)="dialogOpen.set(true)">Open dialog</hum-button>
          <hum-button variant="secondary" (click)="drawerOpen.set(true)">Open drawer</hum-button>
        </div>
        <div class="ui-spec__row">
          <hum-button variant="outline" size="sm" (click)="toast.info('Heads up', 'A new pay run is available.')">Info toast</hum-button>
          <hum-button variant="outline" size="sm" (click)="toast.success('Saved', 'Employee profile updated.')">Success toast</hum-button>
          <hum-button variant="outline" size="sm" (click)="toast.warning('Review needed', 'Some timesheets are unapproved.')"
            >Warning toast</hum-button
          >
          <hum-button variant="outline" size="sm" (click)="toast.danger('Failed', 'Could not reach the gateway.')">Danger toast</hum-button>
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
    .ui-spec__grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 0.75rem;
    }
    .ui-menu-anchor {
      position: relative;
      display: inline-flex;
    }
  `,
})
export default class UiGalleryComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly toast = inject(ToastService);
  protected readonly lastCommand = signal<string | null>(null);

  // Interactive demo state (shared across both panes — toggling reflects in both).
  protected readonly tabValue = signal('overview');
  protected readonly checkboxOn = signal(true);
  protected readonly switchOn = signal(true);
  protected readonly radioValue = signal('weekly');
  protected readonly dialogOpen = signal(false);
  protected readonly drawerOpen = signal(false);

  protected readonly commands: Command[] = [
    { id: 'dashboard', label: 'Go to Dashboard', icon: 'layout-grid', group: 'Navigate', keywords: 'home overview' },
    { id: 'employees', label: 'Employees', icon: 'users', group: 'Navigate', keywords: 'people directory' },
    { id: 'runs', label: 'Pay runs', icon: 'wallet', group: 'Navigate', keywords: 'payroll' },
    { id: 'approvals', label: 'Approvals', icon: 'check-check', group: 'Navigate', hint: '17' },
    { id: 'new-employee', label: 'Add employee', icon: 'user-plus', group: 'Actions', keywords: 'create hire' },
    { id: 'run-payroll', label: 'Run payroll', icon: 'play', group: 'Actions' },
    { id: 'toggle-theme', label: 'Toggle theme', icon: 'moon', group: 'Actions', hint: '⌘⇧L' },
  ];

  protected readonly tabs: TabItem[] = [
    { id: 'overview', label: 'Overview', icon: 'layout-grid' },
    { id: 'compensation', label: 'Compensation' },
    { id: 'leave', label: 'Leave', count: 3 },
    { id: 'documents', label: 'Documents', count: 12 },
  ];

  protected readonly crumbs: Crumb[] = [
    { label: 'Employees', href: '#' },
    { label: 'Engineering', href: '#' },
    { label: 'Amina Cherkaoui' },
  ];

  protected readonly menuItems: MenuItem[] = [
    { heading: 'Account' },
    { id: 'profile', label: 'Profile', icon: 'user' },
    { id: 'settings', label: 'Settings', icon: 'settings', shortcut: '⌘,' },
    { separator: true },
    { id: 'signout', label: 'Sign out', icon: 'log-out', danger: true },
  ];

  protected readonly selectOptions: SelectOption[] = [
    { value: 'eng', label: 'Engineering' },
    { value: 'sales', label: 'Sales' },
    { value: 'ops', label: 'Operations' },
  ];

  protected readonly people: Person[] = [
    { name: 'Amina Cherkaoui' },
    { name: 'Youssef Bennani' },
    { name: 'Sara El Amrani' },
    { name: 'Karim Idrissi' },
    { name: 'Nadia Tazi' },
    { name: 'Omar Fassi' },
  ];

  protected readonly sparkData = [4, 6, 5, 8, 7, 9, 11, 10, 13];

  protected readonly tableColumns: Column[] = [
    { key: 'name', label: 'Employee', sortable: true },
    { key: 'dept', label: 'Department', sortable: true },
    { key: 'net', label: 'Net pay', numeric: true, sortable: true },
    { key: 'status', label: 'Status' },
  ];

  protected readonly tableRows: Row[] = [
    { id: 1, name: 'Amina Cherkaoui', dept: 'Engineering', net: '14,200 MAD', status: 'Active' },
    { id: 2, name: 'Youssef Bennani', dept: 'Sales', net: '11,750 MAD', status: 'On leave' },
    { id: 3, name: 'Sara El Amrani', dept: 'Operations', net: '9,300 MAD', status: 'Active' },
  ];
}
