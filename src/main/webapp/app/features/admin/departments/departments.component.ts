import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  AvatarComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { CreateDepartmentRequest, Department, DepartmentService } from '../index';
import { EmployeeService, personName, type SimpleEmployeeProfile } from 'app/features/employee';
import { PluralPipe } from 'app/shared/util/format.pipes';

/** Accent colours cycled across department cards (same order as the directory's department bar). */
const ACCENTS = [
  'var(--brand)',
  'var(--info-solid)',
  'var(--success-solid)',
  'var(--warning-solid)',
  'var(--violet-500)',
  'var(--danger-solid)',
  'var(--indigo-300)',
  'var(--slate-400)',
];

/**
 * Departments (HR/admin) — every department as a card with its head and its share of the
 * company's headcount, plus an overview strip (departments, people, largest, missing heads).
 * Search and sort are client-side (departments are a small, fully loaded set). Selecting a card
 * opens a panel with the department's people; create/edit is a drawer and delete a confirm
 * dialog, both gated on `MANAGE_DEPARTMENTS`.
 */
@Component({
  selector: 'hum-departments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    LucideAngularModule,
    TranslatePipe,
    PluralPipe,
    AvatarComponent,
    SelectComponent,
    SkeletonComponent,
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

  protected readonly departments = signal<Department[]>([]);
  protected readonly loading = signal(false);
  protected readonly query = signal('');
  protected readonly sortBy = signal<'size' | 'name'>('size');
  protected readonly sortOptions = computed(() => [
    { value: 'size', label: this.label('humano.departments.sortSize') },
    { value: 'name', label: this.label('humano.departments.sortName') },
  ]);

  /** Departments in display order, each with its accent colour (stable per department). */
  protected readonly cards = computed(() => {
    const bySize = [...this.departments()].sort((a, b) => b.employeeCount - a.employeeCount || a.name.localeCompare(b.name));
    const accent = new Map(bySize.map((d, i) => [d.id, ACCENTS[i % ACCENTS.length]]));
    const q = this.query().trim().toLowerCase();
    const list = (this.sortBy() === 'size' ? bySize : [...bySize].sort((a, b) => a.name.localeCompare(b.name))).filter(
      d => !q || d.name.toLowerCase().includes(q) || (d.headName ?? '').toLowerCase().includes(q),
    );
    return list.map(d => ({ ...d, accent: accent.get(d.id) ?? ACCENTS[0] }));
  });
  protected readonly stats = computed(() => {
    const list = this.departments();
    const people = list.reduce((sum, d) => sum + d.employeeCount, 0);
    const largest = list.reduce<Department | null>((max, d) => (!max || d.employeeCount > max.employeeCount ? d : max), null);
    return { count: list.length, people, largest, withoutHead: list.filter(d => !d.headId).length };
  });

  // Detail panel: the selected department and its people.
  protected readonly selected = signal<(Department & { accent: string }) | null>(null);
  protected readonly members = signal<SimpleEmployeeProfile[] | null>(null);
  protected readonly membersTotal = signal(0);
  protected readonly membersError = signal<string | null>(null);
  protected readonly name = personName;
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

  constructor() {
    this.load();
  }

  /** Head picker — employees are unbounded, so search the backend per keystroke. */
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected share(d: Department): number {
    const people = this.stats().people;
    return people ? (d.employeeCount / people) * 100 : 0;
  }

  protected openDetail(d: Department & { accent: string }): void {
    this.selected.set(d);
    this.members.set(null);
    this.membersError.set(null);
    this.employeeService.search({ departmentId: d.id }, { page: 0, size: 50, sort: ['firstName,asc'] }).subscribe({
      next: page => {
        this.members.set(page.content);
        this.membersTotal.set(page.totalElements);
      },
      error: (err: unknown) => this.membersError.set(normalizeHttpError(err)),
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.headInitial.set(null);
    this.form.reset({ name: '', description: '', headId: '' });
    this.formOpen.set(true);
  }

  protected openEdit(department: Department): void {
    this.selected.set(null);
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
        this.selected.set(null);
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
    // Departments are a small set: load them all and search/sort on the client.
    this.departmentService.query({ page: 0, size: 500, sort: ['name,asc'] }).subscribe({
      next: res => {
        this.departments.set(res.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
