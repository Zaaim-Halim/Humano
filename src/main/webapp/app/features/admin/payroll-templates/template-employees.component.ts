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
import { TemplateProfileDrawerComponent } from './template-profile-drawer.component';
import { TemplateInputsGroup, isTemplateSwitch, templateInputLabel, templateInputValues, templateInputsGroup } from './template-inputs';

/**
 * The employees a payroll template covers, on its page: who still lacks required details (payroll
 * can't calculate them), editing one employee's profile ({@link TemplateProfileDrawerComponent}), and
 * setting chosen values on every selected employee at once (`PATCH .../employees`, other values kept).
 * `focusEmployeeId` (from a refused payroll run's link) opens that employee's details once loaded.
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
    TemplateProfileDrawerComponent,
  ],
  templateUrl: './template-employees.component.html',
})
export class TemplateEmployeesComponent {
  readonly template = input.required<PayrollTemplate>();
  /** An employee whose details to open as soon as the list is loaded (once: not again after a save reloads it). */
  readonly focusEmployeeId = input<string | null>(null);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<TemplateEmployee[]>([]);
  protected readonly onlyMissing = signal(false);
  protected readonly search = signal('');
  protected readonly selected = signal(new Set<string>());

  /** The employee whose details are open in the one-employee panel. */
  protected readonly editingOne = signal<TemplateEmployee | null>(null);
  /** The "set details for N selected" panel: how many were selected when it opened. */
  protected readonly editingMany = signal<number | null>(null);
  protected readonly form = signal<TemplateInputsGroup | null>(null);
  /** In the "many" panel: which details to set (the others are left as each employee has them). */
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

  protected readonly label = templateInputLabel;

  private readonly service = inject(PayrollTemplateService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  /** The focus request already acted on, so a reload doesn't reopen the panel. */
  private focusHandled: string | null = null;

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
          this.openFocused(rows);
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
    this.editingOne.set(row);
  }

  /** The one-employee panel saved: close it and re-read what everyone still lacks. */
  protected onOneSaved(): void {
    this.editingOne.set(null);
    this.load();
  }

  protected editSelected(): void {
    this.chosenKeys.set(new Set());
    this.form.set(templateInputsGroup(this.inputs(), {}));
    this.saveError.set(null);
    this.editingMany.set(this.selected().size);
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

  protected closeMany(): void {
    this.editingMany.set(null);
    this.form.set(null);
    this.saveError.set(null);
  }

  protected saveMany(): void {
    const group = this.form();
    if (this.editingMany() === null || !group) return;
    if (group.invalid) {
      group.markAllAsTouched();
      return;
    }
    const id = this.template().id;
    this.saving.set(true);
    this.saveError.set(null);
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
        this.saving.set(false);
        this.closeMany();
        this.toast.success(this.translate.instant('humano.payrollTemplates.employees.savedMany', { count: employeeIds.length }));
        // Re-read the list: it recomputes what each employee still lacks.
        this.load();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.saveError.set(normalizeHttpError(err));
      },
    });
  }

  /** Open the employee a link asked for, once; nothing if they aren't on the list (not covered, or gone). */
  private openFocused(rows: TemplateEmployee[]): void {
    const id = this.focusEmployeeId();
    if (!id || id === this.focusHandled) return;
    this.focusHandled = id;
    const row = rows.find(r => r.employeeId === id);
    if (row) this.editingOne.set(row);
  }
}
