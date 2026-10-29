import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { LEAVE_TYPE_ICON, LeaveType } from 'app/features/employee/models/enums/leave-type.enum';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  SwitchComponent,
  ToastService,
} from 'app/shared/ui';

import { Country } from '../models/country.model';
import { LeavePayBand, LeavePayRule } from '../models/leave-pay-rule.model';
import { CountryService } from '../services/country.service';
import { LeavePayRuleService } from '../services/leave-pay-rule.service';

/** Leave types in the order the screen offers them. */
const LEAVE_TYPES: LeaveType[] = [LeaveType.SICK, LeaveType.MATERNITY, LeaveType.UNPAID, LeaveType.VACATION];

/** One editable pay band; `key` keeps row identity stable. Percentages here are the share PAID. */
interface BandRow {
  key: number;
  fromDay: number | null;
  /** null = to the end of the absence. */
  toDay: number | null;
  paid: number | null;
}

/** The rule being created (`id` null) or edited. */
interface Draft {
  id: string | null;
  countryId: string;
  leaveType: LeaveType | '';
  bands: BandRow[];
  /** Paid share on days no band covers. */
  otherwisePaid: number | null;
  preTax: boolean;
}

interface BandErrors {
  fromDay: boolean;
  toDay: boolean;
  paid: boolean;
  /** Starts on or before the previous band's last day, or follows an open-ended band. */
  overlap: boolean;
}

/**
 * Leave pay — `/payroll/leave-pay` (`MANAGE_PAY_COMPONENTS`): how payroll pays approved sick,
 * parental, unpaid and other leave, per country.
 *
 * <p>A rule says what share of the salary is paid on each working day of leave, optionally in
 * pay bands counted from the absence's first day (e.g. full pay for the first six weeks of
 * sickness, nothing after). The API stores the unpaid share (`deductionPercentage`); the screen
 * shows the paid share, which is how sick pay and maternity pay are usually described. Leave
 * without a rule is paid in full.
 */
@Component({
  selector: 'hum-leave-pay-rules',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    IconButtonComponent,
    InputComponent,
    PageHeaderComponent,
    SelectComponent,
    SkeletonRowComponent,
    SwitchComponent,
  ],
  templateUrl: './leave-pay-rules.component.html',
  styles: `
    .hum-lp-table td {
      vertical-align: top;
    }
    .hum-lp-type {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      font-weight: 600;
    }
    .hum-lp-pay {
      display: grid;
      gap: 2px;
    }
    .hum-lp-actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-1);
    }
    .hum-lp-form {
      display: grid;
      gap: var(--space-4);
    }
    .hum-lp-bands {
      display: grid;
      gap: var(--space-2);
    }
    .hum-lp-band {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr auto;
      gap: var(--space-2);
      align-items: end;
    }
    .hum-lp-band__label {
      display: block;
      font-size: var(--text-xs);
      color: var(--fg-muted);
      margin-bottom: 4px;
    }
    .hum-lp-error {
      color: var(--danger-fg, var(--danger-solid));
      font-size: var(--text-xs);
      margin: 0;
    }
    .hum-lp-origin {
      display: block;
      margin-top: 4px;
    }
    .hum-lp-note {
      display: block;
      margin: 4px 0 0;
      max-width: 48ch;
      color: var(--fg-muted);
      font-size: var(--text-xs);
    }
    .hum-lp-muted {
      color: var(--fg-muted);
      font-size: var(--text-sm);
      margin: 0;
    }
  `,
})
export default class LeavePayRulesComponent {
  private readonly leavePayService = inject(LeavePayRuleService);
  private readonly countryService = inject(CountryService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly typeIcon = LEAVE_TYPE_ICON;

  protected readonly rules = signal<LeavePayRule[] | null>(null);
  protected readonly countries = signal<Country[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly draft = signal<Draft | null>(null);
  protected readonly saving = signal(false);
  protected readonly deleting = signal<LeavePayRule | null>(null);

  protected readonly countryOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.leavePay.form.chooseCountry') },
    ...this.countries().map(c => ({ value: c.id, label: c.name })),
  ]);
  protected readonly typeOptions: SelectOption[] = [
    { value: '', label: this.label('humano.leavePay.form.chooseType') },
    ...LEAVE_TYPES.map(t => ({ value: t, label: this.label(`humano.leave.type.${t}`) })),
  ];

  protected readonly bandErrors = computed<BandErrors[]>(() => {
    const d = this.draft();
    if (!d) return [];
    let nextFree = 1;
    return d.bands.map(b => {
      const fromDay = !isWholeNumber(b.fromDay) || b.fromDay < 1;
      const toDay = b.toDay !== null && (!isWholeNumber(b.toDay) || (!fromDay && b.toDay < b.fromDay!));
      const overlap = !fromDay && b.fromDay! < nextFree;
      nextFree = b.toDay === null ? Number.POSITIVE_INFINITY : b.toDay + 1;
      return { fromDay, toDay, paid: !isPercentage(b.paid), overlap };
    });
  });
  /** The workspace has its own rule for this country and leave type already (creating only); a template's rule may be overridden. */
  protected readonly duplicate = computed(() => {
    const d = this.draft();
    return (
      !!d &&
      d.id === null &&
      (this.rules() ?? []).some(r => r.origin === 'WORKSPACE' && r.countryId === d.countryId && r.leaveType === d.leaveType)
    );
  });
  /** The draft overrides a payroll template's statutory rule (fixed country and leave type). */
  protected readonly overriding = signal<LeavePayRule | null>(null);
  protected readonly valid = computed(() => {
    const d = this.draft();
    return (
      !!d &&
      !!d.countryId &&
      !!d.leaveType &&
      !this.duplicate() &&
      isPercentage(d.otherwisePaid) &&
      this.bandErrors().every(e => !e.fromDay && !e.toDay && !e.paid && !e.overlap)
    );
  });
  /** Whether the last band runs to the end of the absence, so the "otherwise" rate never applies. */
  protected readonly openEnded = computed(() => {
    const bands = this.draft()?.bands ?? [];
    return bands.length > 0 && bands[bands.length - 1].toDay === null;
  });

  private nextKey = 0;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.rules.set(null);
    forkJoin({ rules: this.leavePayService.list(), countries: this.countryService.list() }).subscribe({
      next: ({ rules, countries }) => {
        this.countries.set(countries);
        this.rules.set(rules);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  /** One line per band plus the "otherwise" rate, e.g. "Days 1–42: 100% paid", "After that: unpaid". */
  protected summary(rule: LeavePayRule): string[] {
    const parts = rule.payBands.map(b =>
      b.toDay === null
        ? this.label('humano.leavePay.summary.bandOpen', { from: b.fromDay, paid: this.paidText(b.deductionPercentage) })
        : this.label('humano.leavePay.summary.band', { from: b.fromDay, to: b.toDay, paid: this.paidText(b.deductionPercentage) }),
    );
    const last = rule.payBands.length ? rule.payBands[rule.payBands.length - 1] : null;
    if (last?.toDay !== null) {
      parts.push(
        this.label(rule.payBands.length ? 'humano.leavePay.summary.after' : 'humano.leavePay.summary.everyDay', {
          paid: this.paidText(rule.deductionPercentage),
        }),
      );
    }
    return parts;
  }

  /** Stable row key: template rows have no id. */
  protected rowKey(rule: LeavePayRule): string {
    return rule.id ?? `${rule.templateId}/${rule.countryId}/${rule.leaveType}`;
  }

  protected openNew(): void {
    this.overriding.set(null);
    const countries = new Set((this.rules() ?? []).map(r => r.countryId));
    this.draft.set({
      id: null,
      // The workspace's country when all its rules are for one.
      countryId: countries.size === 1 ? [...countries][0] : '',
      leaveType: '',
      bands: [],
      otherwisePaid: 0,
      preTax: true,
    });
  }

  /** A workspace rule starting from a template's statutory rule, replacing it. */
  protected openOverride(rule: LeavePayRule): void {
    this.overriding.set(rule);
    this.draft.set({
      id: null,
      countryId: rule.countryId,
      leaveType: rule.leaveType,
      bands: rule.payBands.map(b => ({ key: this.nextKey++, fromDay: b.fromDay, toDay: b.toDay, paid: complement(b.deductionPercentage) })),
      otherwisePaid: complement(rule.deductionPercentage),
      preTax: rule.affectsTaxableSalary,
    });
  }

  protected openEdit(rule: LeavePayRule): void {
    this.overriding.set(null);
    this.draft.set({
      id: rule.id,
      countryId: rule.countryId,
      leaveType: rule.leaveType,
      bands: rule.payBands.map(b => ({ key: this.nextKey++, fromDay: b.fromDay, toDay: b.toDay, paid: complement(b.deductionPercentage) })),
      otherwisePaid: complement(rule.deductionPercentage),
      preTax: rule.affectsTaxableSalary,
    });
  }

  protected patch(patch: Partial<Draft>): void {
    this.draft.update(d => (d ? { ...d, ...patch } : d));
  }

  protected patchBand(key: number, patch: Partial<BandRow>): void {
    this.draft.update(d => (d ? { ...d, bands: d.bands.map(b => (b.key === key ? { ...b, ...patch } : b)) } : d));
  }

  protected addBand(): void {
    this.draft.update(d => {
      if (!d) return d;
      const last = d.bands.length ? d.bands[d.bands.length - 1] : null;
      const fromDay = last ? (last.toDay ?? last.fromDay ?? 0) + 1 : 1;
      return { ...d, bands: [...d.bands, { key: this.nextKey++, fromDay, toDay: null, paid: 100 }] };
    });
  }

  protected removeBand(key: number): void {
    this.draft.update(d => (d ? { ...d, bands: d.bands.filter(b => b.key !== key) } : d));
  }

  protected save(): void {
    const d = this.draft();
    if (!d || !this.valid()) return;
    const body = {
      deductionPercentage: complement(d.otherwisePaid!),
      payBands: d.bands.map<LeavePayBand>(b => ({ fromDay: b.fromDay!, toDay: b.toDay, deductionPercentage: complement(b.paid!) })),
      affectsTaxableSalary: d.preTax,
    };
    const request =
      d.id === null
        ? this.leavePayService.create({ ...body, countryId: d.countryId, leaveType: d.leaveType as LeaveType })
        : this.leavePayService.update(d.id, body);
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.draft.set(null);
        this.overriding.set(null);
        this.toast.success(this.label('humano.leavePay.saved'));
        // An override hides the template's rule; the server knows which rules apply.
        this.refresh();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected confirmDelete(): void {
    const rule = this.deleting();
    if (!rule?.id) return;
    this.leavePayService.delete(rule.id).subscribe({
      next: () => {
        this.deleting.set(null);
        this.toast.success(this.label('humano.leavePay.deleted'));
        // Deleting an override brings the template's rule back.
        this.refresh();
      },
      error: (err: unknown) => {
        this.deleting.set(null);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  /** Reads a number input: '' → null. */
  protected num(value: unknown): number | null {
    return value === '' || value === null || value === undefined ? null : Number(value);
  }

  /** Reloads the rules without the loading state. */
  private refresh(): void {
    this.leavePayService.list().subscribe({
      next: rules => this.rules.set(rules),
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  private paidText(deductionPercentage: number): string {
    const paid = complement(deductionPercentage);
    if (paid <= 0) return this.label('humano.leavePay.summary.unpaid');
    return this.label('humano.leavePay.summary.paid', { paid });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}

/** The paid share for an unpaid one and back (100 − x), to the cent the API stores. */
function complement(percentage: number): number {
  return Math.round((100 - percentage) * 100) / 100;
}

function isWholeNumber(value: number | null): value is number {
  return value !== null && Number.isInteger(value);
}

function isPercentage(value: number | null): boolean {
  return value !== null && Number.isFinite(value) && value >= 0 && value <= 100;
}
