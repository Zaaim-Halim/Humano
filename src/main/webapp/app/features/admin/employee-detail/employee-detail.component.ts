import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { PositionChangeRequest, PositionChangeType } from '../models/position-change.model';
import { PositionChangeService } from '../services/position-change.service';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { CompanyService } from 'app/core/company/company.service';
import { EmployeeProfile, EmployeeService } from 'app/features/employee';
import { DocumentLibraryComponent } from 'app/features/employee/documents/document-library.component';
import { CurrentEmployeeService } from 'app/features/employee';
import { EmployeeHeroComponent } from 'app/features/employee/profile/employee-hero.component';
import { EmployeeOverviewComponent } from 'app/features/employee/profile/employee-overview.component';
import { EmployeeOnboardingCardComponent } from '../onboarding/employee-onboarding-card.component';
import { EmployeeOffboardingCardComponent } from '../offboarding/employee-offboarding-card.component';
import { EmployeeRecordsComponent } from 'app/features/employee/employee-records/employee-records.component';
import { EmployeeLeaveComponent } from 'app/features/employee/insights/employee-leave.component';
import { EmployeeCareerComponent } from 'app/features/employee/insights/employee-career.component';
import { EmployeePayComponent } from 'app/features/employee/insights/employee-pay.component';
import { EmployeePerformanceComponent } from 'app/features/employee/insights/employee-performance.component';
import {
  AlertComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';

import {
  Basis,
  CompensationService,
  CreateCompensationRequest,
  Currency,
  CurrencyService,
  Department,
  DepartmentService,
  Position,
  PositionService,
  SalaryAdjustmentRequest,
  SalaryHistory,
} from '../index';

type TabId = 'overview' | 'compensation' | 'leaves' | 'documents' | 'performance' | 'records';

/** Generic per-tab async holder. */
interface TabState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

const idle = <T>(): TabState<T> => ({ data: null, loading: false, error: null });

/**
 * Employee 360 (HR/Admin hero screen) — identity header + tabbed detail.
 * Identity is `GET /api/hr/employees/{id}`; each tab **lazy-loads on first
 * activation** from its own endpoint with independent loading/empty/error
 * states (no spinner-only screens). `id` is route-bound via
 * `withComponentInputBinding()`. CompensationService/LeaveRequestService/etc.
 * are cross-persona imports from the Employee barrel.
 */
@Component({
  selector: 'hum-employee-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    LucideAngularModule,
    ReactiveFormsModule,
    TranslatePipe,
    ButtonComponent,
    TabsComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    InputComponent,
    SelectComponent,
    FormFieldComponent,
    EmployeeRecordsComponent,
    DocumentLibraryComponent,
    EmployeeHeroComponent,
    EmployeeOverviewComponent,
    EmployeeOnboardingCardComponent,
    EmployeeOffboardingCardComponent,
    EmployeePayComponent,
    EmployeeCareerComponent,
    EmployeeLeaveComponent,
    EmployeePerformanceComponent,
  ],
  templateUrl: './employee-detail.component.html',
})
export default class EmployeeDetailComponent {
  /** Employee id from the route (`/employees/:id`). */
  readonly id = input.required<string>();

  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly compensationService = inject(CompensationService);
  private readonly positionService = inject(PositionService);
  private readonly departmentService = inject(DepartmentService);
  private readonly positionChangeService = inject(PositionChangeService);
  private readonly currencyService = inject(CurrencyService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  /** New salaries start from the company's currency and pay basis (Settings). */
  private readonly company = inject(CompanyService);
  private readonly account = inject(AccountService);

  /** Gate the Edit action on the same permission the edit route requires. */
  protected readonly canEdit = this.account.hasPermission(Permission.UPDATE_EMPLOYEE);
  /** Gate document upload/delete on the same permission the write endpoints require. */
  protected readonly canManageDocuments = this.account.hasPermission(Permission.MANAGE_EMPLOYEE_DOCUMENTS);

  // Identity.
  protected readonly identity = signal<TabState<EmployeeProfile>>(idle());
  /** Salary history as last loaded by the pay tab; bump `compReload` to refresh it. */
  protected readonly compHistory = signal<SalaryHistory | null>(null);
  protected readonly compReload = signal(0);

  /** Gate the compensation editor on the compensation write permission. */
  protected readonly canManageCompensation = this.account.hasPermission(Permission.MANAGE_COMPENSATION);

  // Compensation editor — creates the first record, then adjusts thereafter
  // (a second active compensation would conflict on the backend).
  protected readonly compFormOpen = signal(false);
  protected readonly compSaving = signal(false);
  protected readonly compMode = computed<'create' | 'adjust'>(() => (this.compHistory()?.currentCompensation ? 'adjust' : 'create'));

  private readonly positions = signal<Position[]>([]);
  private readonly currencies = signal<Currency[]>([]);
  protected readonly positionOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.compensationEditor.selectPosition') },
    ...this.positions().map(p => ({ value: p.id, label: p.name })),
  ]);
  protected readonly currencyOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.compensationEditor.selectCurrency') },
    ...this.currencies().map(c => ({ value: c.id, label: `${c.code} — ${c.name}` })),
  ]);
  protected readonly basisOptions: SelectOption[] = Object.values(Basis).map(b => ({ value: b, label: b }));
  protected readonly adjustBasisOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.compensationEditor.keepBasis') },
    ...Object.values(Basis).map(b => ({ value: b, label: b })),
  ]);

  // Amount controls hold strings (hum-input emits strings); coerced on submit.
  protected readonly createForm = this.fb.nonNullable.group({
    positionId: ['', Validators.required],
    currencyId: ['', Validators.required],
    baseAmount: ['', [Validators.required, Validators.min(0.01)]],
    basis: [Basis.MONTHLY as Basis, Validators.required],
    effectiveFrom: ['', Validators.required],
    effectiveTo: [''],
  });
  protected readonly adjustForm = this.fb.nonNullable.group({
    newAmount: ['', [Validators.required, Validators.min(0.01)]],
    newBasis: [''],
    effectiveFrom: ['', Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  // Position change — a promotion, transfer or correction, dated and kept in the history.
  protected readonly changeOpen = signal(false);
  protected readonly changeSaving = signal(false);
  /** Bump to reload the career history after recording a change. */
  protected readonly careerReload = signal(0);
  private readonly departments = signal<Department[]>([]);
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly changeTypeOptions = computed<SelectOption[]>(() =>
    (['PROMOTION', 'TRANSFER', 'DEMOTION', 'OTHER'] as PositionChangeType[]).map(t => ({
      value: t,
      label: this.translate.instant(`humano.positionChange.types.${t}`),
    })),
  );
  protected readonly changePositionOptions = computed<SelectOption[]>(() => {
    const current = this.identity().data;
    return [
      { value: '', label: this.translate.instant('humano.positionChange.keep', { name: current?.positionName ?? '—' }) },
      ...this.positions()
        .filter(p => p.id !== current?.positionId)
        .map(p => ({ value: p.id, label: p.name })),
    ];
  });
  protected readonly changeDepartmentOptions = computed<SelectOption[]>(() => {
    const current = this.identity().data;
    return [
      { value: '', label: this.translate.instant('humano.positionChange.keep', { name: current?.departmentName ?? '—' }) },
      ...this.departments()
        .filter(d => d.id !== current?.departmentId)
        .map(d => ({ value: d.id, label: d.name })),
    ];
  });
  protected readonly changeForm = this.fb.nonNullable.group({
    changeType: ['PROMOTION' as PositionChangeType, Validators.required],
    effectiveDate: ['', Validators.required],
    newPositionId: [''],
    newDepartmentId: [''],
    newSalary: [''],
    reason: ['', [Validators.required, Validators.maxLength(255)]],
  });

  protected readonly activeTab = signal<TabId>('overview');

  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'overview', label: this.translate.instant('humano.employee360.tabOverview'), icon: 'layout-grid' },
    { id: 'compensation', label: this.translate.instant('humano.employee360.tabCompensation'), icon: 'wallet' },
    { id: 'leaves', label: this.translate.instant('humano.employee360.tabLeaves'), icon: 'palmtree' },
    { id: 'documents', label: this.translate.instant('humano.employee360.tabDocuments'), icon: 'file-text' },
    { id: 'performance', label: this.translate.instant('humano.employee360.tabPerformance'), icon: 'star' },
    { id: 'records', label: this.translate.instant('humano.employee360.tabRecords'), icon: 'folder' },
  ]);

  constructor() {
    // Identity (re)loads whenever the route id changes.
    effect(() => {
      const id = this.id();
      if (id) {
        untracked(() => this.loadIdentity(id));
      }
    });
  }

  private loadIdentity(id: string): void {
    this.compHistory.set(null);
    this.identity.set({ data: null, loading: true, error: null });
    this.employeeService.find(id).subscribe({
      next: data => this.identity.set({ data, loading: false, error: null }),
      error: (err: unknown) => this.identity.set({ data: null, loading: false, error: normalizeHttpError(err) }),
    });
  }

  protected retryIdentity(): void {
    this.loadIdentity(this.id());
  }

  protected openCompEditor(): void {
    if (this.compMode() === 'adjust') {
      this.adjustForm.reset({ newAmount: '', newBasis: '', effectiveFrom: '', reason: '' });
    } else {
      this.createForm.reset({
        positionId: this.identity().data?.positionId ?? '',
        currencyId: this.company.profile()?.defaultCurrencyId ?? '',
        baseAmount: '',
        basis: (this.company.profile()?.defaultBasis as Basis | undefined) ?? Basis.MONTHLY,
        effectiveFrom: '',
        effectiveTo: '',
      });
    }
    // Reference data is bounded; load once and cache.
    if (this.positions().length === 0) this.loadPositions();
    if (this.currencies().length === 0) this.loadCurrencies();
    this.compFormOpen.set(true);
  }

  protected compInvalid(control: string): boolean {
    const form: FormGroup = this.compMode() === 'adjust' ? this.adjustForm : this.createForm;
    const c = form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected saveCompensation(): void {
    const employeeId = this.id();
    if (this.compMode() === 'adjust') {
      if (this.adjustForm.invalid) {
        this.adjustForm.markAllAsTouched();
        return;
      }
      const raw = this.adjustForm.getRawValue();
      const body: SalaryAdjustmentRequest = {
        employeeId,
        newAmount: Number(raw.newAmount),
        effectiveFrom: raw.effectiveFrom,
        reason: raw.reason,
        ...(raw.newBasis ? { newBasis: raw.newBasis as Basis } : {}),
      };
      this.submitCompensation(this.compensationService.adjust(body));
    } else {
      if (this.createForm.invalid) {
        this.createForm.markAllAsTouched();
        return;
      }
      const raw = this.createForm.getRawValue();
      const body: CreateCompensationRequest = {
        employeeId,
        positionId: raw.positionId,
        currencyId: raw.currencyId,
        baseAmount: Number(raw.baseAmount),
        basis: raw.basis,
        effectiveFrom: raw.effectiveFrom,
        ...(raw.effectiveTo ? { effectiveTo: raw.effectiveTo } : {}),
      };
      this.submitCompensation(this.compensationService.create(body));
    }
  }

  private submitCompensation(request$: Observable<unknown>): void {
    this.compSaving.set(true);
    request$.subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.compensationEditor.saved'));
        this.compFormOpen.set(false);
        this.compSaving.set(false);
        this.compReload.update(n => n + 1);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.compSaving.set(false);
      },
    });
  }

  protected openPositionChange(): void {
    this.changeForm.reset({
      changeType: 'PROMOTION',
      effectiveDate: this.today,
      newPositionId: '',
      newDepartmentId: '',
      newSalary: '',
      reason: '',
    });
    if (this.positions().length === 0) this.loadPositions();
    // The current salary, for the hint (the pay tab loads it only when opened).
    if (this.canManageCompensation && !this.compHistory()) {
      this.compensationService.history(this.id()).subscribe({ next: h => this.compHistory.set(h), error: () => undefined });
    }
    if (this.departments().length === 0) {
      this.departmentService
        .query({ page: 0, size: 200, sort: ['name,asc'] })
        .subscribe({ next: res => this.departments.set(res.content) });
    }
    this.changeOpen.set(true);
  }

  protected changeInvalid(control: string): boolean {
    const c = this.changeForm.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected savePositionChange(): void {
    if (this.changeForm.invalid) {
      this.changeForm.markAllAsTouched();
      return;
    }
    const raw = this.changeForm.getRawValue();
    if (!raw.newPositionId && !raw.newDepartmentId && !raw.newSalary) {
      this.toast.danger(this.translate.instant('humano.positionChange.nothing'));
      return;
    }
    const body: PositionChangeRequest = {
      changeType: raw.changeType,
      effectiveDate: raw.effectiveDate,
      reason: raw.reason.trim(),
      ...(raw.newPositionId ? { newPositionId: raw.newPositionId } : {}),
      ...(raw.newDepartmentId ? { newDepartmentId: raw.newDepartmentId } : {}),
      ...(raw.newSalary ? { newSalary: Number(raw.newSalary) } : {}),
    };
    this.changeSaving.set(true);
    this.positionChangeService.change(this.id(), body).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.positionChange.saved'));
        this.changeOpen.set(false);
        this.changeSaving.set(false);
        this.careerReload.update(n => n + 1);
        this.compReload.update(n => n + 1);
        this.loadIdentity(this.id());
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.changeSaving.set(false);
      },
    });
  }

  private loadPositions(): void {
    this.positionService.query({ page: 0, size: 200, sort: ['name,asc'] }).subscribe({
      next: res => this.positions.set(res.content),
    });
  }

  private loadCurrencies(): void {
    this.currencyService.list().subscribe({ next: res => this.currencies.set(res) });
  }

  /** Can change this person's photo: HR with update rights, or the person themselves. */
  protected canChangePhoto(e: EmployeeProfile): boolean {
    return this.canEdit || this.currentEmployee.currentEmployeeId() === e.id;
  }

  protected onImageChanged(imageUrl: string | null): void {
    this.identity.update(st => (st.data ? { ...st, data: { ...st.data, imageUrl } } : st));
  }

  protected back(): void {
    void this.router.navigate(['/employees']);
  }

  protected goEdit(): void {
    void this.router.navigate(['/employees', this.id(), 'edit']);
  }
}
