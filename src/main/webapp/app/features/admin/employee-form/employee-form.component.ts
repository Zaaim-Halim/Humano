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
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AvatarComponent,
  BadgeComponent,
  AutocompleteOption,
  ButtonComponent,
  CardComponent,
  CheckboxComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import {
  DepartmentService,
  EmployeeCategoryService,
  EmploymentTypeService,
  JobGradeService,
  JobLevelService,
  MaritalStatusService,
  OrganizationalUnitService,
  PositionService,
  ReferenceData,
} from 'app/features/admin';
import { ROLE_GROUPS, humanizeCode } from 'app/features/admin/models/user-admin.model';
import { OnboardingService } from 'app/features/admin/services/onboarding.service';
import {
  EmployeeAttributeService,
  EmployeeService,
  EmployeeStatus,
  personName,
  type CreateEmployeeRequest,
  WorkLocationType,
  type EmployeeProfile,
  type UpdateEmployeeProfileRequest,
} from 'app/features/employee';
import type { EmployeePersonalDetails } from 'app/features/employee/models/employee.model';

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
 * Create / edit an employee profile (HR) — one page of sectioned cards with an in-page menu:
 * account, job & placement, employment (classification from Settings → Lists, work location,
 * FTE, probation), personal details, government ids (edit only, write-only: blank keeps the
 * stored value; the current one shows masked), access & roles (grouped by area), custom fields.
 * A sticky bar saves and flags unsaved changes.
 *
 * - **Edit** (`/employees/:id/edit`) loads the profile and `PUT`s changes.
 * - **Create** (`/employees/new`) provisions the account and HR profile together
 *   via `POST /api/hr/employees` (one step). There is no separate user
 *   management and no self-registration — every employee is added here.
 *
 * Country is intentionally omitted: no country reference endpoint is exposed to
 * the frontend yet, and `countryId` is optional on the API.
 */
/** Classification fields backed by Settings → Lists. */
type RefKey = 'employmentType' | 'level' | 'grade' | 'category' | 'maritalStatus';
const REF_KEYS: RefKey[] = ['employmentType', 'level', 'grade', 'category', 'maritalStatus'];

@Component({
  selector: 'hum-employee-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    AvatarComponent,
    BadgeComponent,
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
  private readonly onboardingService = inject(OnboardingService);
  private readonly account = inject(AccountService);
  private readonly attributeService = inject(EmployeeAttributeService);
  private readonly departmentService = inject(DepartmentService);
  private readonly positionService = inject(PositionService);
  private readonly unitService = inject(OrganizationalUnitService);

  /** Edit target id (null in create mode). */
  protected readonly employeeId = signal<string | null>(null);
  protected readonly isEdit = computed(() => this.employeeId() !== null);
  /** Opt-in: start onboarding right after creating the employee (off by default). */
  protected readonly canStartOnboarding = this.account.hasPermission(Permission.MANAGE_ONBOARDING);
  protected readonly startOnboarding = signal(false);

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

  /**
   * Classification lists (Settings → Lists): only entries switched on are offered, but an
   * employee's current value is kept (marked hidden) so editing never silently clears it.
   */
  private readonly refLists = signal<Record<RefKey, ReferenceData[]>>({
    employmentType: [],
    level: [],
    grade: [],
    category: [],
    maritalStatus: [],
  });
  private readonly refCurrent = signal<Partial<Record<RefKey, string>>>({});
  protected readonly refOptions = computed(() => {
    const out = {} as Record<RefKey, SelectOption[]>;
    for (const key of REF_KEYS) {
      const current = this.refCurrent()[key];
      const entries = this.refLists()[key].filter(e => e.active || e.id === current);
      out[key] = [
        { value: '', label: this.translate.instant('humano.employeeForm.notSet') },
        ...entries.map(e => ({
          value: e.id,
          label: e.active ? e.name : `${e.name} (${this.translate.instant('humano.employeeForm.hiddenEntry')})`,
        })),
      ];
    }
    return out;
  });
  private readonly employmentTypeService = inject(EmploymentTypeService);
  private readonly jobLevelService = inject(JobLevelService);
  private readonly jobGradeService = inject(JobGradeService);
  private readonly employeeCategoryService = inject(EmployeeCategoryService);
  private readonly maritalStatusService = inject(MaritalStatusService);
  /** Seeds the manager autocomplete with the current value's label in edit mode. */
  protected readonly managerInitial = signal<AutocompleteOption | null>(null);

  protected readonly statusOptions: SelectOption[] = [
    { value: '', label: this.translate.instant('humano.employeeForm.statusUnset') },
    ...Object.values(EmployeeStatus).map(s => ({ value: s, label: this.translate.instant(`humano.employeeForm.statusValue.${s}`) })),
  ];
  protected readonly genderOptions: SelectOption[] = [
    { value: '', label: this.translate.instant('humano.employeeForm.notSet') },
    ...['MALE', 'FEMALE', 'NON_BINARY', 'OTHER', 'PREFER_NOT_TO_SAY'].map(g => ({
      value: g,
      label: this.translate.instant(`humano.employee360.genderValue.${g}`),
    })),
  ];
  protected readonly locationOptions: SelectOption[] = [
    { value: '', label: this.translate.instant('humano.employeeForm.notSet') },
    ...['ONSITE', 'HYBRID', 'REMOTE'].map(l => ({ value: l, label: this.translate.instant(`humano.directory.location.${l}`) })),
  ];

  /** The profile being edited (header and masked government ids). */
  protected readonly profile = signal<EmployeeProfile | null>(null);
  protected readonly fullName = computed(() => {
    const p = this.profile();
    return p ? [p.firstName, p.lastName].filter(Boolean).join(' ') || (p.jobTitle ?? '') : '';
  });

  /** Sections, for the in-page menu (government ids only once the employee exists). */
  protected readonly sections = computed(() =>
    [
      { id: 'account', icon: 'user', create: true, edit: true },
      { id: 'job', icon: 'briefcase', create: true, edit: true },
      { id: 'employment', icon: 'file-signature', create: true, edit: true },
      { id: 'personal', icon: 'id-card', create: true, edit: true },
      { id: 'ids', icon: 'shield-check', create: false, edit: true },
      { id: 'access', icon: 'key-round', create: true, edit: true },
      { id: 'custom', icon: 'list', create: false, edit: true },
    ].filter(s => (this.isEdit() ? s.edit : s.create)),
  );

  /** Roles grouped by area (same groups as Settings → Users & access), unknown roles last. */
  protected readonly roleGroups = computed(() => {
    const assignable = new Set(this.assignableRoles());
    const grouped = new Set(ROLE_GROUPS.flatMap(g => g.roles));
    const groups = ROLE_GROUPS.map(g => ({ ...g, roles: g.roles.filter(r => assignable.has(r)) })).filter(g => g.roles.length);
    const other = [...assignable].filter(r => !grouped.has(r));
    return other.length ? [...groups, { id: 'other', icon: 'tag', roles: other }] : groups;
  });
  private readonly initialRoles = signal<string>('');
  /** Unsaved changes: any field, or the role selection. */
  protected readonly rolesChanged = computed(() => [...this.selectedRoles()].sort().join() !== this.initialRoles());

  protected jump(id: string): void {
    document.getElementById(`emp-section-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Last four characters of a stored government id ("•••• 1234"). */
  protected masked(value: string | null | undefined): string {
    return value ? `•••• ${value.slice(-4)}` : '';
  }

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
      // Classification — choices come from Settings → Lists.
      employmentTypeId: [''],
      levelId: [''],
      gradeId: [''],
      categoryId: [''],
      maritalStatusId: [''],
      // Employment details.
      employeeNumber: ['', Validators.maxLength(50)],
      workLocation: [''],
      fte: ['', [Validators.min(1), Validators.max(100)]],
      probationEndDate: [''],
      confirmationDate: [''],
      // Personal.
      birthDate: [''],
      gender: [''],
      placeOfBirth: ['', Validators.maxLength(100)],
      workPhone: ['', Validators.maxLength(40)],
      // Government ids — write-only: blank keeps the stored value.
      nationalId: ['', Validators.maxLength(50)],
      passportNumber: ['', Validators.maxLength(50)],
      taxNumber: ['', Validators.maxLength(50)],
      socialSecurityNumber: ['', Validators.maxLength(50)],
    },
    { validators: dateOrder },
  );

  /**
   * Manager picker source — employees are unbounded, so this hits the backend
   * search per keystroke rather than pre-loading a page. `query` matches (OR) on
   * first name, last name, and job title; the option labels on the person's name
   * (falling back to job title) with the job title as sublabel.
   */
  protected readonly searchManagers = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

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

  /** "ROLE_HR_MANAGER" → "HR manager" (same names as Settings → Users & access). */
  protected readonly roleLabel = humanizeCode;

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
        if (!id && this.canStartOnboarding && this.startOnboarding()) {
          this.startOnboardingFor(saved);
          return;
        }
        this.saving.set(false);
        void this.router.navigate(['/employees', saved.id]);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  /**
   * Optional follow-up to creating someone: start their onboarding (company default length, from
   * their start date). The employee is kept if this fails — they can be onboarded from their profile.
   */
  private startOnboardingFor(saved: EmployeeProfile): void {
    const startDate = this.form.getRawValue().startDate || undefined;
    this.onboardingService.initiate({ employeeId: saved.id, ...(startDate ? { startDate } : {}) }).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.employeeForm.onboardingStarted'));
        this.saving.set(false);
        void this.router.navigate(['/employees', saved.id]);
      },
      error: (err: unknown) => {
        this.toast.warning(this.translate.instant('humano.employeeForm.onboardingFailed'), normalizeHttpError(err));
        this.saving.set(false);
        void this.router.navigate(['/employees', saved.id]);
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
      ...(v.employmentTypeId ? { employmentType: { id: v.employmentTypeId } } : {}),
      ...(v.levelId ? { level: { id: v.levelId } } : {}),
      ...(v.gradeId ? { grade: { id: v.gradeId } } : {}),
      ...(v.categoryId ? { category: { id: v.categoryId } } : {}),
      ...(v.maritalStatusId ? { maritalStatus: { id: v.maritalStatusId } } : {}),
      personalDetails: this.compact<EmployeePersonalDetails>({
        employeeNumber: v.employeeNumber.trim(),
        workLocation: (v.workLocation || undefined) as WorkLocationType | undefined,
        fte: v.fte ? Number(v.fte) / 100 : undefined,
        probationEndDate: v.probationEndDate,
        confirmationDate: v.confirmationDate,
        birthDate: v.birthDate,
        gender: v.gender,
        placeOfBirth: v.placeOfBirth.trim(),
        workPhone: v.workPhone.trim(),
      }),
    };
  }

  /** Drops blank entries — the API leaves omitted fields unchanged. */
  private compact<T extends object>(obj: T): T {
    return Object.fromEntries(Object.entries(obj).filter(([, val]) => val !== '' && val !== undefined && val !== null)) as T;
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

  /** Update body: profile fields + full role replacement; government ids only when retyped. */
  private buildUpdateBody(): UpdateEmployeeProfileRequest {
    const v = this.form.getRawValue();
    const ids = this.compact({
      nationalId: v.nationalId.trim(),
      passportNumber: v.passportNumber.trim(),
      taxNumber: v.taxNumber.trim(),
      socialSecurityNumber: v.socialSecurityNumber.trim(),
    });
    return {
      ...(Object.keys(ids).length ? { governmentIds: ids } : {}),
      startDate: v.startDate,
      positionId: v.positionId,
      unitId: v.unitId,
      authorities: [...this.selectedRoles()],
      ...this.profileFields(),
    };
  }

  /** Bringing a terminated employee back clears their leaving date; switching back restores it. */
  private clearEndDateOnReinstate(leftOn: string): void {
    this.form.controls.status.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(status => {
      const endDate = this.form.controls.endDate;
      if (status !== EmployeeStatus.TERMINATED && endDate.value === leftOn) endDate.setValue('');
      else if (status === EmployeeStatus.TERMINATED && !endDate.value) endDate.setValue(leftOn);
    });
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
            employmentTypeId: p.employmentType?.id ?? '',
            levelId: p.level?.id ?? '',
            gradeId: p.grade?.id ?? '',
            categoryId: p.category?.id ?? '',
            maritalStatusId: p.maritalStatus?.id ?? '',
            employeeNumber: p.personalDetails?.employeeNumber ?? '',
            workLocation: p.personalDetails?.workLocation ?? '',
            fte: p.personalDetails?.fte ? String(Math.round(Number(p.personalDetails.fte) * 100)) : '',
            probationEndDate: p.personalDetails?.probationEndDate ?? '',
            confirmationDate: p.personalDetails?.confirmationDate ?? '',
            birthDate: p.personalDetails?.birthDate ?? '',
            gender: p.personalDetails?.gender ?? '',
            placeOfBirth: p.personalDetails?.placeOfBirth ?? '',
            workPhone: p.personalDetails?.workPhone ?? '',
          });
          // Position and department change through Change position (dated, kept in the history).
          this.form.controls.positionId.disable();
          this.form.controls.departmentId.disable();
          if (p.status === EmployeeStatus.TERMINATED) this.clearEndDateOnReinstate(p.endDate?.slice(0, 10) ?? '');
          this.profile.set(p);
          this.refCurrent.set({
            employmentType: p.employmentType?.id ?? undefined,
            level: p.level?.id ?? undefined,
            grade: p.grade?.id ?? undefined,
            category: p.category?.id ?? undefined,
            maritalStatus: p.maritalStatus?.id ?? undefined,
          });
          this.managerInitial.set(
            p.managerId ? { value: p.managerId, label: p.managerName ?? p.managerId, sublabel: p.managerJobTitle } : null,
          );
          this.selectedRoles.set(new Set(p.authorities));
          this.initialRoles.set([...p.authorities].sort().join());
          this.form.markAsPristine();
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loadError.set(normalizeHttpError(err));
          this.loading.set(false);
        },
      });
  }

  private loadReferenceLists(): void {
    const req = { page: 0, size: 500, sort: ['name,asc'] };
    const sources: Record<RefKey, { query: (r: typeof req) => Observable<{ content: ReferenceData[] }> }> = {
      employmentType: this.employmentTypeService,
      level: this.jobLevelService,
      grade: this.jobGradeService,
      category: this.employeeCategoryService,
      maritalStatus: this.maritalStatusService,
    };
    for (const key of REF_KEYS) {
      sources[key]
        .query(req)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: page => this.refLists.update(all => ({ ...all, [key]: page.content })) });
    }
  }

  private loadOptions(): void {
    this.loadReferenceLists();
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
