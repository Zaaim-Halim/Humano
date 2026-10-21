import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';
import { countryName } from 'app/shared/util/countries';

import { PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { templateState } from './template-state';

/**
 * Payroll → Templates (`/payroll/templates`): the statutory payroll templates this build ships, the
 * ones this workspace uses first — enabled, then adopted but not switched on (how a template chosen
 * at sign-up starts) — then the rest by country. Each opens its settings.
 */
@Component({
  selector: 'hum-payroll-templates',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './payroll-templates.component.html',
})
export default class PayrollTemplatesComponent {
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Adopted templates (in use or set aside) and the rest of the catalog. */
  protected readonly groups = computed(() => {
    const all = [...this.templates()].sort(
      (a, b) =>
        rank(a) - rank(b) ||
        this.country(a).localeCompare(this.country(b)) ||
        b.validTo.localeCompare(a.validTo) ||
        a.id.localeCompare(b.id),
    );
    return [
      { key: 'inUse', templates: all.filter(t => t.adoption) },
      { key: 'available', templates: all.filter(t => !t.adoption) },
    ].filter(g => g.templates.length);
  });

  protected readonly state = templateState;

  private readonly service = inject(PayrollTemplateService);
  private readonly templates = signal<PayrollTemplate[]>([]);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: list => {
        this.templates.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected country(t: PayrollTemplate): string {
    return countryName(t.country) + (t.subdivision ? ` — ${t.subdivision}` : '');
  }
}

function rank(t: PayrollTemplate): number {
  return t.adoption?.enabled ? 0 : t.adoption ? 1 : 2;
}
