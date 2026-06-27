import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  ButtonComponent,
  CardComponent,
  CheckboxComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { DepartmentService, OrganizationalUnitService, PositionService } from 'app/features/admin';
import {
  EmployeeAttributeService,
  EmployeeService,
  EmployeeStatus,
  personName,
  type CreateEmployeeRequest,
  type EmployeeProfile,
  type UpdateEmployeeProfileRequest,
} from 'app/features/employee';

/** Client-side username pattern; the backend enforces the full LOGIN_REGEX. */
const LOGIN_PATTERN = /^[_.@A-Za-z0-9-]+$/;

type AttributeRow = FormGroup<{ key: FormControl<string>; value: FormControl<string> }>;

/** End date, when present, must not precede the start date. */
function dateOrder(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;
  return start && end && end < start ? { dateOrder: true } : null;
}

/**
 * Create / edit an employee profile (HR).
 *
 * - **Edit** (`/employees/:id/edit`) loads the profile and `PUT`s changes.
 * - **Create** (`/employees/new`) provisions the account and HR profile together
 *   via `POST /api/hr/employees` (one step). There is no separate user
 *   management and no self-registration — every employee is added here.
 *
 * Country is intentionally omitted: no country reference endpoint is exposed to
 * the frontend yet, and `countryId` is optional on the API.
 */
@Component({
  selector: 'hum-employee-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    CheckboxComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    AutocompleteComponent,
    ButtonComponent,
    AlertComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './employee-form.component.html',
})
export default class EmployeeFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly employeeService = inject(EmployeeService);
  private readonly attributeService = inject(EmployeeAttributeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly positionService = inject(PositionService);
  private readonly unitService = inject(OrganizationalUnitService);

  /** Edit target id (null in create mode). */
  protected readonly employeeId = signal<string | null>(null);
  protected readonly isEdit = computed(() => this.employeeId() !== null);

  /** Roles assignable on the form (from the backend), and the current selection. */
  protected readonly assignableRoles = signal<string[]>([]);
  protected readonly selectedRoles = signal<Set<string>>(new Set());

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly attributesSaving = signal(false);
  protected readonly attributesLoading = signal(false);
  // A load failure must NOT look like "no attributes" — otherwise a replace-all
  // save would wipe the real (just-unloaded) set. Saving is blocked until load succeeds.
  protected readonly attributesLoadError = signal<string | null>(null);

  /** Custom key/value attributes (edit mode only — they attach to a saved employee). */
  protected readonly attributes = this.fb.array<AttributeRow>([]);

  protected readonly departments = signal<SelectOption[]>([]);
  protected readonly positions = signal<SelectOption[]>([]);
  protected readonly units = signal<SelectOption[]>([]);
  /** Seeds the manager autocomplete with the current value's label in edit mode. */
  protected readonly managerInitial = signal<AutocompleteOption | null>(null);

  protected readonly statusOptions: SelectOption[] = [
    { value: '', label: this.translate.instant('humano.employeeForm.statusUnset') },
    ...Object.values(EmployeeStatus).map(s => ({ value: s, label: s })),
  ];

  protected readonly form = this.fb.nonNullable.group(
    {
      // Account / identity — create mode only (cleared in edit; the account is fixed).
      login: ['', [Validators.required, Validators.pattern(LOGIN_PATTERN), Validators.maxLength(50)]],
      firstName: ['', [Validators.maxLength(50)]],
      lastName: ['', [Validators.maxLength(50)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      // HR profile.
      jobTitle: ['', [Validators.maxLength(100)]],
      phone: ['', [Validators.maxLength(40)]],
      startDate: ['', [Validators.required]],
      endDate: [''],
      status: [''],
      departmentId: [''],
      positionId: ['', [Validators.required]],
      unitId: ['', [Validators.required]],
      managerId: [''],
    },
    { validators: dateOrder },
  );

  /**
   * Manager picker source — employees are unbounded, so this hits the backend
   * search per keystroke rather than pre-loading a page. `query` matches (OR) on
   * first name, last name, and job title; the option labels on the person's name
   * (falling back to job title) with the job title as sublabel.
   */
  protected readonly searchManagers = (term: string): Observable<AutocompleteOption[]> =>
    this.employeeService
      .search({ query: term }, { page: 0, size: 10 })
      .pipe(map(res => res.content.map(e => ({ value: e.id, label: personName(e), sublabel: e.jobTitle ?? e.departmentName }))));

  ngOnInit(): void {
    this.loadOptions();
    this.loadAssignableRoles();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      // Edit: the account (login/email) is fixed and not rendered, so drop its
      // required validators to keep the form submittable.
      this.employeeId.set(id);
      for (const name of ['login', 'firstName', 'lastName', 'email']) {
        const c = this.form.get(name);
        c?.clearValidators();
        c?.updateValueAndValidity();
      }
      this.loadProfile(id);
      this.loadAttributes(id);
    }
  }

  /** Toggle a role in the selection (immutably, so OnPush picks up the change). */
  protected toggleRole(role: string, checked: boolean): void {
    this.selectedRoles.update(current => {
      const next = new Set(current);
      if (checked) {
        next.add(role);
      } else {
        next.delete(role);
      }
      return next;
    });
  }

  protected isRoleSelected(role: string): boolean {
    return this.selectedRoles().has(role);
  }

  private loadAssignableRoles(): void {
    this.employeeService
      .getAssignableRoles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(roles => this.assignableRoles.set(roles));
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected addAttribute(key = '', value = ''): void {
    this.attributes.push(
      this.fb.nonNullable.group({
        key: this.fb.nonNullable.control(key, [Validators.required, Validators.maxLength(255)]),
        value: this.fb.nonNullable.control(value, [Validators.required, Validators.maxLength(1000)]),
      }),
    );
  }

  protected removeAttribute(index: number): void {
    this.attributes.removeAt(index);
    this.attributes.markAsDirty();
  }

  protected saveAttributes(): void {
    const id = this.employeeId();
    // Never replace-all when the current set never loaded — that would wipe real data.
    if (!id || this.attributesLoadError()) return;
    if (this.attributes.invalid) {
      this.attributes.markAllAsTouched();
      return;
    }

    const rows = this.attributes.getRawValue().map(r => ({ key: r.key.trim(), value: r.value }));
    if (new Set(rows.map(r => r.key)).size !== rows.length) {
      this.toast.danger(this.translate.instant('humano.employeeForm.duplicateKeys'));
      return;
    }

    this.attributesSaving.set(true);
    this.attributeService
      .replaceForEmployee(id, rows)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(this.translate.instant('humano.employeeForm.attributesSaved'));
          this.attributesSaving.set(false);
        },
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.attributesSaving.set(false);
        },
      });
  }

  protected retryAttributes(): void {
    const id = this.employeeId();
    if (id) this.loadAttributes(id);
  }

  private loadAttributes(id: string): void {
    this.attributesLoading.set(true);
    this.attributesLoadError.set(null);
    this.attributeService
      .getForEmployee(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: attrs => {
          this.attributes.clear();
          attrs.forEach(a => this.addAttribute(a.key, a.value));
          this.attributesLoading.set(false);
        },
        error: (err: unknown) => {
          // Leave the editor unrendered so an empty list can't be saved over the real data.
          this.attributesLoadError.set(normalizeHttpError(err));
          this.attributesLoading.set(false);
        },
      });
  }

  protected cancel(): void {
    const id = this.employeeId();
    void this.router.navigate(id ? ['/employees', id] : ['/employees']);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);

    const id = this.employeeId();
    const request$ = id ? this.employeeService.update(id, this.buildUpdateBody()) : this.employeeService.create(this.buildCreateBody());

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved: EmployeeProfile) => {
        this.toast.success(this.translate.instant(id ? 'humano.employeeForm.updated' : 'humano.employeeForm.created'));
        this.saving.set(false);
        void this.router.navigate(['/employees', saved.id]);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  /** Shared HR-profile fields; required go in flat, optional omitted when blank. */
  private profileFields(): UpdateEmployeeProfileRequest {
    const v = this.form.getRawValue();
    return {
      ...(v.jobTitle ? { jobTitle: v.jobTitle } : {}),
      ...(v.phone ? { phone: v.phone } : {}),
      ...(v.endDate ? { endDate: v.endDate } : {}),
      ...(v.status ? { status: v.status as EmployeeStatus } : {}),
      ...(v.departmentId ? { departmentId: v.departmentId } : {}),
      ...(v.managerId ? { managerId: v.managerId } : {}),
    };
  }

  /** Create body: account identity + roles + required profile fields. */
  private buildCreateBody(): CreateEmployeeRequest {
    const v = this.form.getRawValue();
    return {
      login: v.login,
      email: v.email,
      ...(v.firstName ? { firstName: v.firstName } : {}),
      ...(v.lastName ? { lastName: v.lastName } : {}),
      authorities: [...this.selectedRoles()],
      startDate: v.startDate,
      positionId: v.positionId,
      unitId: v.unitId,
      ...this.profileFields(),
    };
  }

  /** Update body: profile fields + full role replacement. */
  private buildUpdateBody(): UpdateEmployeeProfileRequest {
    const v = this.form.getRawValue();
    return {
      startDate: v.startDate,
      positionId: v.positionId,
      unitId: v.unitId,
      authorities: [...this.selectedRoles()],
      ...this.profileFields(),
    };
  }

  private loadProfile(id: string): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.employeeService
      .find(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p: EmployeeProfile) => {
          this.form.patchValue({
            jobTitle: p.jobTitle ?? '',
            phone: p.phone ?? '',
            startDate: p.startDate?.slice(0, 10) ?? '',
            endDate: p.endDate?.slice(0, 10) ?? '',
            status: p.status,
            departmentId: p.departmentId ?? '',
            positionId: p.positionId ?? '',
            unitId: p.unitId ?? '',
            managerId: p.managerId ?? '',
          });
          this.managerInitial.set(p.managerId ? { value: p.managerId, label: p.managerInfo ?? p.managerId } : null);
          this.selectedRoles.set(new Set(p.authorities));
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loadError.set(normalizeHttpError(err));
          this.loading.set(false);
        },
      });
  }

  private loadOptions(): void {
    const page = { size: 200, sort: ['name,asc'] };
    const withBlank = (opts: SelectOption[], placeholder: string): SelectOption[] => [{ value: '', label: placeholder }, ...opts];

    this.departmentService
      .query(page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res =>
        this.departments.set(
          withBlank(
            res.content.map(d => ({ value: d.id, label: d.name })),
            this.translate.instant('humano.employeeForm.departmentUnset'),
          ),
        ),
      );

    this.positionService
      .query(page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res =>
        this.positions.set(
          withBlank(
            res.content.map(p => ({ value: p.id, label: p.name })),
            this.translate.instant('humano.employeeForm.select'),
          ),
        ),
      );

    this.unitService
      .query(page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res =>
        this.units.set(
          withBlank(
            res.content.map(u => ({ value: u.id, label: u.name })),
            this.translate.instant('humano.employeeForm.select'),
          ),
        ),
      );
  }
}
