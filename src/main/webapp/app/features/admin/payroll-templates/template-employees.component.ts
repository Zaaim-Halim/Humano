import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DrawerComponent,
  InputComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { PayrollTemplate, TemplateEmployee, TemplateInput } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { TemplateInputsComponent } from './template-inputs.component';
import {
  TemplateInputsGroup,
  isTemplateSwitch,
  requireTemplateInputs,
  templateInputLabel,
  templateInputValues,
  templateInputsGroup,
} from './template-inputs';

/** The drawer: one employee's whole profile, or chosen values for every selected employee. */
type Editing = { mode: 'one'; employee: TemplateEmployee } | { mode: 'many'; count: number };

/**
 * The employees a payroll template covers, on its page: who still lacks required details (payroll
 * can't calculate them), editing one employee's profile (`PUT .../employees/{id}`, replaces it), and
 * setting chosen values on every selected employee at once (`PATCH .../employees`, other values kept).
 */
@Component({
  selector: 'hum-template-employees',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    DrawerComponent,
    InputComponent,
    SkeletonRowComponent,
    TemplateInputsComponent,
  ],
  templateUrl: './template-employees.component.html',
})
export class TemplateEmployeesComponent {
  readonly template = input.required<PayrollTemplate>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<TemplateEmployee[]>([]);
  protected readonly onlyMissing = signal(false);
  protected readonly search = signal('');
  protected readonly selected = signal(new Set<string>());

  protected readonly editing = signal<Editing | null>(null);
  protected readonly form = signal<TemplateInputsGroup | null>(null);
  /** In the "many" drawer: which details to set (the others are left as each employee has them). */
  protected readonly chosenKeys = signal(new Set<string>());
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly inputs = computed(() => this.template().employeeInputs);
  protected readonly chosenInputs = computed(() => this.inputs().filter(i => this.chosenKeys().has(i.key)));
  protected readonly missingCount = computed(() => this.rows().filter(r => r.missing.length).length);
  protected readonly visible = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.rows().filter(
      r =>
        (!this.onlyMissing() || r.missing.length > 0) &&
        (!q || [r.name, r.employeeNumber, r.jobTitle, r.department].some(v => v?.toLowerCase().includes(q))),
    );
  });
  protected readonly allVisibleSelected = computed(() => {
    const list = this.visible();
    return list.length > 0 && list.every(r => this.selected().has(r.employeeId));
  });
  protected readonly drawerTitle = computed((): string => {
    const e = this.editing();
    if (!e) return '';
    return e.mode === 'one'
      ? e.employee.name
      : String(this.translate.instant('humano.payrollTemplates.employees.setForTitle', { count: e.count }));
  });

  protected readonly label = templateInputLabel;

  private readonly service = inject(PayrollTemplateService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      const id = this.template().id;
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.template().id): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .employees(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          this.rows.set(rows);
          // Keep only selections still on the list.
          const ids = new Set(rows.map(r => r.employeeId));
          this.selected.update(s => new Set([...s].filter(x => ids.has(x))));
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(normalizeHttpError(err));
          this.loading.set(false);
        },
      });
  }

  protected missingLabels(row: TemplateEmployee): string {
    return row.missing.map(templateInputLabel).join(', ');
  }

  protected toggle(id: string, on: boolean): void {
    this.selected.update(s => {
      const next = new Set(s);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected toggleAllVisible(on: boolean): void {
    const ids = this.visible().map(r => r.employeeId);
    this.selected.update(s => {
      const next = new Set(s);
      ids.forEach(id => (on ? next.add(id) : next.delete(id)));
      return next;
    });
  }

  protected clearSelection(): void {
    this.selected.set(new Set());
  }

  /** Select everyone still missing details — the usual first bulk step after switching a template on. */
  protected selectMissing(): void {
    this.selected.set(
      new Set(
        this.rows()
          .filter(r => r.missing.length)
          .map(r => r.employeeId),
      ),
    );
  }

  protected edit(row: TemplateEmployee): void {
    const group = templateInputsGroup(this.inputs(), row.attributes);
    // A profile is saved to make the employee payable: its required details are required here.
    requireTemplateInputs(group, this.inputs(), true);
    this.open({ mode: 'one', employee: row }, group);
  }

  protected editSelected(): void {
    this.chosenKeys.set(new Set());
    this.open({ mode: 'many', count: this.selected().size }, templateInputsGroup(this.inputs(), {}));
  }

  protected choose(field: TemplateInput, on: boolean): void {
    this.chosenKeys.update(keys => {
      const next = new Set(keys);
      if (on) next.add(field.key);
      else next.delete(field.key);
      return next;
    });
    // A chosen detail needs a value (a switch always has one); an unchosen one isn't sent.
    const control = this.form()?.controls[field.key];
    if (control && !isTemplateSwitch(field)) {
      if (on) control.addValidators(Validators.required);
      else control.removeValidators(Validators.required);
      control.updateValueAndValidity();
    }
  }

  protected close(): void {
    this.editing.set(null);
    this.form.set(null);
    this.saveError.set(null);
  }

  protected save(): void {
    const editing = this.editing();
    const group = this.form();
    if (!editing || !group) return;
    if (group.invalid) {
      group.markAllAsTouched();
      return;
    }
    const id = this.template().id;
    this.saving.set(true);
    this.saveError.set(null);
    if (editing.mode === 'one') {
      this.service.saveProfile(id, editing.employee.employeeId, templateInputValues(this.inputs(), group)).subscribe({
        next: () => this.saved('humano.payrollTemplates.employees.savedOne', { name: editing.employee.name }),
        error: (err: unknown) => this.failed(err),
      });
      return;
    }
    const chosen = this.chosenInputs();
    if (!chosen.length) {
      this.saving.set(false);
      this.saveError.set(this.translate.instant('humano.payrollTemplates.employees.chooseOne'));
      return;
    }
    const employeeIds = [...this.selected()];
    this.service.updateProfiles(id, { employeeIds, attributes: templateInputValues(chosen, group) }).subscribe({
      next: () => {
        this.selected.set(new Set());
        this.saved('humano.payrollTemplates.employees.savedMany', { count: employeeIds.length });
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  private open(editing: Editing, group: TemplateInputsGroup): void {
    this.form.set(group);
    this.saveError.set(null);
    this.editing.set(editing);
  }

  private saved(key: string, params: Record<string, unknown>): void {
    this.saving.set(false);
    this.close();
    this.toast.success(this.translate.instant(key, params));
    // Re-read the list: it recomputes what each employee still lacks.
    this.load();
  }

  private failed(err: unknown): void {
    this.saving.set(false);
    this.saveError.set(normalizeHttpError(err));
  }
}
