import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  EmployeeDocument,
  EmployeeDocumentService,
  EmployeeProfile,
  EmployeeService,
  LeaveRequest,
  LeaveRequestService,
  PerformanceReview,
  PerformanceReviewService,
} from 'app/features/employee';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
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
  Position,
  PositionService,
  SalaryAdjustmentRequest,
  SalaryHistory,
} from '../index';

type TabId = 'overview' | 'compensation' | 'leaves' | 'documents' | 'performance';

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
    ReactiveFormsModule,
    TranslatePipe,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    IconButtonComponent,
    TabsComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    SelectComponent,
    FormFieldComponent,
  ],
  templateUrl: './employee-detail.component.html',
})
export default class EmployeeDetailComponent {
  /** Employee id from the route (`/employees/:id`). */
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly compensationService = inject(CompensationService);
  private readonly positionService = inject(PositionService);
  private readonly currencyService = inject(CurrencyService);
  private readonly leaveService = inject(LeaveRequestService);
  private readonly documentService = inject(EmployeeDocumentService);
  private readonly performanceService = inject(PerformanceReviewService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);
  private readonly account = inject(AccountService);

  /** Gate the Edit action on the same permission the edit route requires. */
  protected readonly canEdit = this.account.hasPermission(Permission.UPDATE_EMPLOYEE);
  /** Gate document upload/delete on the same permission the write endpoints require. */
  protected readonly canManageDocuments = this.account.hasPermission(Permission.MANAGE_EMPLOYEE_DOCUMENTS);

  // Document upload drawer + delete confirm.
  protected readonly uploadOpen = signal(false);
  protected readonly uploading = signal(false);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly uploadForm = this.fb.nonNullable.group({
    type: ['', [Validators.maxLength(255)]],
  });
  protected readonly deleteTarget = signal<EmployeeDocument | null>(null);
  protected readonly deleting = signal(false);

  // TODO: backend — the employee profile DTOs expose no person name (only jobTitle);
  // firstName/lastName live on User/MeResponse, not the employee profile. Using
  // jobTitle as the display identity until a user-join is available.

  // Identity.
  protected readonly identity = signal<TabState<EmployeeProfile>>(idle());
  // Lazy tabs.
  protected readonly compensation = signal<TabState<SalaryHistory>>(idle());
  protected readonly leaves = signal<TabState<LeaveRequest[]>>(idle());
  protected readonly documents = signal<TabState<EmployeeDocument[]>>(idle());
  protected readonly performance = signal<TabState<PerformanceReview[]>>(idle());

  /** Gate the compensation editor on the compensation write permission. */
  protected readonly canManageCompensation = this.account.hasPermission(Permission.MANAGE_COMPENSATION);

  // Compensation editor — creates the first record, then adjusts thereafter
  // (a second active compensation would conflict on the backend).
  protected readonly compFormOpen = signal(false);
  protected readonly compSaving = signal(false);
  protected readonly compMode = computed<'create' | 'adjust'>(() => (this.compensation().data?.currentCompensation ? 'adjust' : 'create'));

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

  protected readonly activeTab = signal<TabId>('overview');
  private readonly loadedKeys = new Set<string>();

  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'overview', label: this.translate.instant('humano.employee360.tabOverview') },
    { id: 'compensation', label: this.translate.instant('humano.employee360.tabCompensation') },
    { id: 'leaves', label: this.translate.instant('humano.employee360.tabLeaves') },
    { id: 'documents', label: this.translate.instant('humano.employee360.tabDocuments') },
    { id: 'performance', label: this.translate.instant('humano.employee360.tabPerformance') },
  ]);

  constructor() {
    // Identity (re)loads whenever the route id changes.
    effect(() => {
      const id = this.id();
      if (id) {
        untracked(() => this.loadIdentity(id));
      }
    });

    // Lazy-load the active tab's data on first activation (keyed by id+tab).
    effect(() => {
      const id = this.id();
      const tab = this.activeTab();
      if (!id || tab === 'overview') return;
      const key = `${id}:${tab}`;
      if (this.loadedKeys.has(key)) return;
      this.loadedKeys.add(key);
      untracked(() => this.loadTab(id, tab));
    });
  }

  private loadIdentity(id: string): void {
    this.loadedKeys.clear();
    this.compensation.set(idle());
    this.leaves.set(idle());
    this.documents.set(idle());
    this.performance.set(idle());
    this.identity.set({ data: null, loading: true, error: null });
    this.employeeService.find(id).subscribe({
      next: data => this.identity.set({ data, loading: false, error: null }),
      error: (err: unknown) => this.identity.set({ data: null, loading: false, error: normalizeHttpError(err) }),
    });
  }

  protected retryIdentity(): void {
    this.loadIdentity(this.id());
  }

  protected retryTab(tab: TabId): void {
    const id = this.id();
    // Drop the cache key; loadTab re-runs and the activation guard re-adds it on success.
    this.loadedKeys.delete(`${id}:${tab}`);
    this.loadedKeys.add(`${id}:${tab}`);
    this.loadTab(id, tab);
  }

  private loadTab(id: string, tab: TabId): void {
    switch (tab) {
      case 'compensation':
        this.compensation.set({ data: null, loading: true, error: null });
        this.compensationService.history(id).subscribe({
          next: data => this.compensation.set({ data, loading: false, error: null }),
          error: (err: unknown) => this.compensation.set({ data: null, loading: false, error: normalizeHttpError(err) }),
        });
        break;
      case 'leaves':
        this.leaves.set({ data: null, loading: true, error: null });
        this.leaveService.searchByEmployee(id, {}, { size: 50, sort: ['startDate,desc'] }).subscribe({
          next: page => this.leaves.set({ data: page.content, loading: false, error: null }),
          error: (err: unknown) => this.leaves.set({ data: null, loading: false, error: normalizeHttpError(err) }),
        });
        break;
      case 'documents':
        this.documents.set({ data: null, loading: true, error: null });
        this.documentService.forEmployee(id).subscribe({
          next: data => this.documents.set({ data, loading: false, error: null }),
          error: (err: unknown) => this.documents.set({ data: null, loading: false, error: normalizeHttpError(err) }),
        });
        break;
      case 'performance':
        this.performance.set({ data: null, loading: true, error: null });
        this.performanceService.search({ employeeId: id }, { size: 50, sort: ['reviewDate,desc'] }).subscribe({
          next: page => this.performance.set({ data: page.content, loading: false, error: null }),
          error: (err: unknown) => this.performance.set({ data: null, loading: false, error: normalizeHttpError(err) }),
        });
        break;
    }
  }

  protected download(doc: EmployeeDocument): void {
    this.documentService.download(doc.id).subscribe({
      next: res => {
        const blob = res.body;
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = this.document.createElement('a');
        a.href = url;
        a.download = doc.filePath?.split('/').pop() ?? `${doc.type ?? 'document'}-${doc.id}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  protected openUpload(): void {
    this.uploadForm.reset({ type: '' });
    this.selectedFile.set(null);
    this.uploadOpen.set(true);
  }

  protected onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.selectedFile.set(target.files?.[0] ?? null);
  }

  protected submitUpload(): void {
    const file = this.selectedFile();
    if (!file) return;
    const type = this.uploadForm.getRawValue().type.trim();
    this.uploading.set(true);
    this.documentService.uploadForEmployee(this.id(), file, type ? { type } : {}).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.employee360.docUploaded'));
        this.uploadOpen.set(false);
        this.uploading.set(false);
        this.reloadDocuments();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.uploading.set(false);
      },
    });
  }

  protected confirmDeleteDocument(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.documentService.delete(target.id).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.employee360.docDeleted'));
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.reloadDocuments();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.deleting.set(false);
      },
    });
  }

  /** Re-fetch the documents tab after a write (reuses the tab's cache-busting retry). */
  private reloadDocuments(): void {
    this.retryTab('documents');
  }

  protected openCompEditor(): void {
    if (this.compMode() === 'adjust') {
      this.adjustForm.reset({ newAmount: '', newBasis: '', effectiveFrom: '', reason: '' });
    } else {
      this.createForm.reset({
        positionId: this.identity().data?.positionId ?? '',
        currencyId: '',
        baseAmount: '',
        basis: Basis.MONTHLY,
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
        this.retryTab('compensation');
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.compSaving.set(false);
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

  protected back(): void {
    void this.router.navigate(['/employees']);
  }

  protected goEdit(): void {
    void this.router.navigate(['/employees', this.id(), 'edit']);
  }
}
