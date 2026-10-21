import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  SwitchComponent,
  ToastService,
} from 'app/shared/ui';
import { countryName } from 'app/shared/util/countries';

import { PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { TemplateEmployeesComponent } from './template-employees.component';
import { TemplateInputsComponent } from './template-inputs.component';
import { TemplateInputsGroup, requireTemplateInputs, templateInputValues, templateInputsGroup } from './template-inputs';
import { templateState } from './template-state';

/**
 * One statutory payroll template (`/payroll/templates/:id`): its employer settings, whether payroll
 * uses it, its employees' profiles ({@link TemplateEmployeesComponent}), what changed since it was
 * last reviewed, and what it covers (limitations, sources).
 * <p>
 * Settings and the on/off switch are saved together (`PUT .../adoption`). The template's required
 * settings are only required while it is switched on — the server's rule too — so a template chosen
 * at sign-up can be saved switched off and half filled in. Enabling returns what in the workspace
 * interacts with the template (e.g. employees without a profile); those warnings are shown as sent.
 */
@Component({
  selector: 'hum-payroll-template',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    PageHeaderComponent,
    SkeletonRowComponent,
    SwitchComponent,
    TemplateEmployeesComponent,
    TemplateInputsComponent,
  ],
  templateUrl: './payroll-template.component.html',
})
export default class PayrollTemplateComponent {
  /** Template id from the route (`/payroll/templates/:id`). */
  readonly id = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly acknowledging = signal(false);
  /** What the server reported about the workspace when the template was last enabled here. */
  protected readonly warnings = signal<string[]>([]);
  protected readonly template = signal<PayrollTemplate | null>(null);

  /** Whether payroll uses the template. Saved with the settings, not on its own. */
  protected readonly enabled = new FormControl(false, { nonNullable: true });
  protected readonly settings = signal<TemplateInputsGroup | null>(null);

  protected readonly state = computed(() => {
    const t = this.template();
    return t ? templateState(t) : null;
  });
  protected readonly country = computed(() => {
    const t = this.template();
    return t ? countryName(t.country) + (t.subdivision ? ` — ${t.subdivision}` : '') : '';
  });
  /** Why the template can't be switched on, if it can't (drafts; state/municipal templates). */
  protected readonly blockedKey = computed(() => {
    const t = this.template();
    if (!t) return null;
    if (!t.adoptable) return 'humano.payrollTemplates.blocked.draft';
    if (t.subdivision) return 'humano.payrollTemplates.blocked.subdivision';
    return null;
  });
  /** Changelog entries newer than the version last reviewed here, newest first. */
  protected readonly newChanges = computed(() => {
    const t = this.template();
    const seen = t?.adoption?.acknowledgedVersion;
    if (!t?.adoption?.updateAvailable || !seen) return [];
    return t.changelog.filter(c => compareVersions(c.version, seen) > 0).sort((a, b) => compareVersions(b.version, a.version));
  });

  private readonly service = inject(PayrollTemplateService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
    this.enabled.valueChanges.pipe(takeUntilDestroyed()).subscribe(on => {
      const group = this.settings();
      const t = this.template();
      if (group && t) requireTemplateInputs(group, t.employerInputs, on);
    });
  }

  protected load(id = this.id()): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: t => {
          this.show(t);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(normalizeHttpError(err));
          this.loading.set(false);
        },
      });
  }

  protected save(): void {
    const t = this.template();
    const group = this.settings();
    if (!t || !group) return;
    if (group.invalid) {
      group.markAllAsTouched();
      return;
    }
    const enable = this.enabled.value;
    this.saving.set(true);
    this.saveError.set(null);
    this.service.configure(t.id, { enabled: enable, settings: templateInputValues(t.employerInputs, group) }).subscribe({
      next: result => {
        this.show(result.template);
        this.warnings.set(result.warnings);
        this.saving.set(false);
        this.toast.success(this.translate.instant(enable ? 'humano.payrollTemplates.savedOn' : 'humano.payrollTemplates.savedOff'));
      },
      error: (err: unknown) => {
        // Business rules (another template already on for the country, a setting out of range) come back as 400s.
        this.saveError.set(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  protected acknowledge(): void {
    const t = this.template();
    if (!t) return;
    this.acknowledging.set(true);
    this.service.acknowledge(t.id).subscribe({
      next: updated => {
        this.template.set(updated);
        this.acknowledging.set(false);
        this.toast.success(this.translate.instant('humano.payrollTemplates.update.reviewed'));
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.acknowledging.set(false);
      },
    });
  }

  /** Show a (re)loaded template: a fresh settings form from its saved settings, the switch as saved. */
  private show(t: PayrollTemplate): void {
    const on = !!t.adoption?.enabled;
    const group = templateInputsGroup(t.employerInputs, t.adoption?.settings ?? {});
    requireTemplateInputs(group, t.employerInputs, on);
    this.template.set(t);
    this.settings.set(group);
    this.enabled.setValue(on, { emitEvent: false });
    // A template that can't be enabled can still be switched off (if it somehow is on), never on.
    if (this.blockedKey() && !on) {
      this.enabled.disable({ emitEvent: false });
    } else {
      this.enabled.enable({ emitEvent: false });
    }
    this.enabled.markAsPristine();
  }
}

/** `2026.1.0` vs `2026.0.3`: numeric parts compared in order. */
function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}
