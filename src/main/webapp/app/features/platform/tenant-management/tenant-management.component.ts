import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { createListResource, normalizeHttpError } from 'app/core/api';
import { ThemeService } from 'app/core/theme/theme.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonRowComponent,
  StatTileComponent,
  ToastService,
} from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { Tenant, TenantDetail, TenantStatus } from '../index';
import { TenantService } from '../index';

/**
 * Tenant Management (Platform/Superadmin hero screen) — violet chrome. Lists
 * tenants (status-filterable) with row → detail Drawer (`GET /{id}` incl. live
 * pool stats), and suspend / activate / deprovision lifecycle actions
 * (deprovision guarded by a confirm Dialog). Sets `data-chrome="platform"` on
 * the root via `ThemeService` while active, restoring `app` on destroy (3.4).
 *
 * TODO: backend — `TenantResponse` exposes no MRR or health fields; the spec's
 * MRR/health KPI tiles + columns are omitted (no fake data). Showing total +
 * active counts and plan/org-count, which the contract does provide.
 */
@Component({
  selector: 'hum-tenant-management',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    StatTileComponent,
    SelectComponent,
    BadgeComponent,
    ButtonComponent,
    DrawerComponent,
    DialogComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './tenant-management.component.html',
})
export default class TenantManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly tenantService = inject(TenantService);
  private readonly theme = inject(ThemeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly filters = this.fb.nonNullable.group({ status: '' });
  private readonly status = signal<TenantStatus | undefined>(undefined);

  protected readonly statusOptions = computed(() => [
    { value: '', label: this.translate.instant('humano.tenants.all') },
    ...Object.values(TenantStatus).map(s => ({ value: s, label: s })),
  ]);

  protected readonly list = createListResource<Tenant>(req => this.tenantService.list(this.status(), req), { initial: { size: 25 } });

  // Detail drawer.
  protected readonly selected = signal<Tenant | null>(null);
  protected readonly detail = signal<TenantDetail | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);
  protected readonly poolEntries = computed(() => Object.entries(this.detail()?.poolStats ?? {}));

  protected readonly deleteTarget = signal<Tenant | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    // Violet platform chrome while this surface is mounted (3.4).
    this.theme.setChrome('platform');
    inject(DestroyRef).onDestroy(() => this.theme.setChrome('app'));

    this.filters.valueChanges.pipe(takeUntilDestroyed()).subscribe(v => {
      this.status.set((v.status as TenantStatus) || undefined);
      this.list.setParams({ page: 0 });
    });
  }

  protected openDetail(tenant: Tenant): void {
    this.selected.set(tenant);
    this.loadDetail(tenant.id);
  }

  protected reloadDetail(): void {
    const t = this.selected();
    if (t) this.loadDetail(t.id);
  }

  private loadDetail(id: string): void {
    this.detailLoading.set(true);
    this.detailError.set(null);
    this.tenantService.find(id).subscribe({
      next: d => {
        this.detail.set(d);
        this.detailLoading.set(false);
      },
      error: (err: unknown) => {
        this.detailError.set(normalizeHttpError(err));
        this.detailLoading.set(false);
      },
    });
  }

  protected closeDetail(): void {
    this.selected.set(null);
    this.detail.set(null);
    this.detailError.set(null);
  }

  protected suspend(t: Tenant): void {
    this.act(this.tenantService.suspend(t.id), 'humano.tenants.suspended');
  }

  protected activate(t: Tenant): void {
    this.act(this.tenantService.activate(t.id), 'humano.tenants.activated');
  }

  protected confirmDeprovision(): void {
    const t = this.deleteTarget();
    if (!t) return;
    this.busy.set(true);
    this.tenantService.deprovision(t.id).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.tenants.deprovisioned'));
        this.busy.set(false);
        this.deleteTarget.set(null);
        this.closeDetail();
        this.list.reload();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.busy.set(false);
      },
    });
  }

  private act(call: ReturnType<TenantService['suspend']>, successKey: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.toast.success(stripHtml(this.translate.instant(successKey)));
        this.busy.set(false);
        this.closeDetail();
        this.list.reload();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.busy.set(false);
      },
    });
  }
}
