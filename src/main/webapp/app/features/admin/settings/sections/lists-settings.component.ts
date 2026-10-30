import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  SkeletonRowComponent,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { ReferenceData } from '../../models/reference-data.model';
import { EmployeeCategoryService } from '../../services/employee-category.service';
import { EmploymentTypeService } from '../../services/employment-type.service';
import { JobGradeService } from '../../services/job-grade.service';
import { JobLevelService } from '../../services/job-level.service';
import { MaritalStatusService } from '../../services/marital-status.service';
import { TerminationReasonService } from '../../services/termination-reason.service';

interface ListDef {
  id: string;
  icon: string;
  service: {
    query(req?: any): import('rxjs').Observable<{ content: ReferenceData[] }>;
    create(body: any): import('rxjs').Observable<ReferenceData>;
    update(id: string, body: any): import('rxjs').Observable<ReferenceData>;
    delete(id: string): import('rxjs').Observable<void>;
  };
}

/**
 * Lists — the choices offered across the app's forms: employment types, job levels, job grades,
 * employee categories, marital statuses and termination reasons.
 *
 * Pick a list on the left; its entries show with their code, name, notes and an on/off switch
 * (inactive entries stay on existing records but are no longer offered). Add or edit in a side
 * panel (the code is a stable identifier, upper-cased); delete asks first and explains when an
 * entry is still in use.
 */
@Component({
  selector: 'hum-lists-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    FormFieldComponent,
    IconButtonComponent,
    InputComponent,
    SkeletonRowComponent,
    SwitchComponent,
    TextareaComponent,
  ],
  templateUrl: './lists-settings.component.html',
})
export class ListsSettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly lists: ListDef[] = [
    { id: 'employmentTypes', icon: 'briefcase', service: inject(EmploymentTypeService) },
    { id: 'jobLevels', icon: 'bar-chart-3', service: inject(JobLevelService) },
    { id: 'jobGrades', icon: 'layers', service: inject(JobGradeService) },
    { id: 'employeeCategories', icon: 'tag', service: inject(EmployeeCategoryService) },
    { id: 'maritalStatuses', icon: 'heart', service: inject(MaritalStatusService) },
    { id: 'terminationReasons', icon: 'log-out', service: inject(TerminationReasonService) },
  ];

  protected readonly current = signal(this.lists[0]);
  protected readonly entries = signal<ReferenceData[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    return (this.entries() ?? [])
      .filter(e => !q || `${e.code} ${e.name}`.toLowerCase().includes(q))
      .sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name));
  });
  /** Entry counts per list, filled as lists are opened. */
  protected readonly counts = signal<Record<string, number>>({});

  // Editor
  protected readonly editing = signal<ReferenceData | 'new' | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9_]+$/)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    notes: ['', Validators.maxLength(500)],
    active: [true],
  });
  protected readonly deleteTarget = signal<ReferenceData | null>(null);

  constructor() {
    effect(() => {
      const list = this.current();
      untracked(() => this.load(list));
    });
  }

  protected load(list = this.current()): void {
    this.entries.set(null);
    this.error.set(null);
    list.service.query({ page: 0, size: 500 }).subscribe({
      next: page => {
        this.entries.set(page.content);
        this.counts.update(c => ({ ...c, [list.id]: page.content.length }));
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected select(list: ListDef): void {
    this.query.set('');
    this.current.set(list);
  }

  protected openNew(): void {
    this.form.reset({ code: '', name: '', notes: '', active: true });
    this.form.controls.code.enable();
    this.editing.set('new');
  }

  protected openEdit(e: ReferenceData): void {
    this.form.reset({ code: e.code, name: e.name, notes: e.notes ?? '', active: e.active });
    this.editing.set(e);
  }

  /** Suggests a code from the name for new entries ("Fixed term" → "FIXED_TERM"). */
  protected suggestCode(): void {
    if (this.editing() !== 'new' || this.form.controls.code.dirty) return;
    const code = this.form.controls.name.value
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_|_$/g, '');
    this.form.controls.code.setValue(code);
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body = { code: raw.code.trim().toUpperCase(), name: raw.name.trim(), notes: raw.notes.trim() || undefined, active: raw.active };
    const target = this.editing();
    const list = this.current();
    this.saving.set(true);
    (target === 'new' ? list.service.create(body) : list.service.update((target as ReferenceData).id, body)).subscribe({
      next: saved => {
        this.saving.set(false);
        this.editing.set(null);
        this.entries.update(all => (target === 'new' ? [...(all ?? []), saved] : (all ?? []).map(e => (e.id === saved.id ? saved : e))));
        this.toast.success(this.t('humano.settings.saved'));
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected toggle(e: ReferenceData, active: boolean): void {
    this.current()
      .service.update(e.id, { active })
      .subscribe({
        next: saved => this.entries.update(all => (all ?? []).map(x => (x.id === saved.id ? saved : x))),
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.load();
        },
      });
  }

  protected confirmDelete(): void {
    const e = this.deleteTarget();
    if (!e) return;
    this.current()
      .service.delete(e.id)
      .subscribe({
        next: () => {
          this.deleteTarget.set(null);
          this.entries.update(all => (all ?? []).filter(x => x.id !== e.id));
          this.toast.success(this.t('humano.settings.lists.deleted'));
        },
        error: () => {
          this.deleteTarget.set(null);
          this.toast.danger(this.t('humano.settings.lists.inUse'));
        },
      });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
