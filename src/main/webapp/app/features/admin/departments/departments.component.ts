import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { DEFAULT_PAGE_SIZE, normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateDepartmentRequest, Department, DepartmentService } from '../index';
import { EmployeeService } from 'app/features/employee';

/**
 * Departments management (HR/admin) — paged list with a create/edit drawer and
 * delete confirm. The department head is an employee, picked via the
 * backend-search autocomplete. Manage actions are gated on `MANAGE_DEPARTMENTS`.
 */
@Component({
  selector: 'hum-departments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    ButtonComponent,
    IconButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    TextareaComponent,
    AutocompleteComponent,
    FormFieldComponent,
  ],
  templateUrl: './departments.component.html',
})
export default class DepartmentsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly departmentService = inject(DepartmentService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_DEPARTMENTS);

  protected readonly size = DEFAULT_PAGE_SIZE;
  protected readonly departments = signal<Department[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<Department | null>(null);
  protected readonly deleteTarget = signal<Department | null>(null);
  protected readonly deleting = signal(false);
  protected readonly headInitial = signal<AutocompleteOption | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.maxLength(1000)]],
    headId: [''],
  });

  /** Head picker — employees are unbounded, so search the backend per keystroke. */
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> =>
    this.employeeService
      .search({ jobTitle: term }, { page: 0, size: 10 })
      .pipe(map(res => res.content.map(e => ({ value: e.id, label: e.jobTitle ?? e.id, sublabel: e.departmentName }))));

  constructor() {
    this.load();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected goto(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.headInitial.set(null);
    this.form.reset({ name: '', description: '', headId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(department: Department): void {
    this.editing.set(department);
    this.headInitial.set(department.headId ? { value: department.headId, label: department.headName ?? department.headId } : null);
    this.form.reset({
      name: department.name,
      description: department.description ?? '',
      headId: department.headId ?? '',
    });
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: CreateDepartmentRequest = {
      name: raw.name,
      ...(raw.description ? { description: raw.description } : {}),
      ...(raw.headId ? { headId: raw.headId } : {}),
    };

    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.departmentService.update(editing.id, body) : this.departmentService.create(body);
    request$.subscribe({
      next: () => {
        this.toast.success(this.translate.instant(editing ? 'humano.departments.updated' : 'humano.departments.created'));
        this.formOpen.set(false);
        this.saving.set(false);
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.departmentService.delete(target.id).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.departments.deleted'));
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.deleting.set(false);
      },
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.departmentService.query({ page: this.page(), size: this.size, sort: ['name,asc'] }).subscribe({
      next: res => {
        this.departments.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
