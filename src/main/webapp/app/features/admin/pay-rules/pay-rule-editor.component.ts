import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { PayRuleService } from '../services/pay-rule.service';
import {
  CreatePayRuleRequest,
  FormulaMetadata,
  FormulaValidationResult,
  FunctionMeta,
  PayComponent,
  PayRuleSummary,
} from '../models/pay-rule.model';

/**
 * Pay-rule formula editor (HR/admin) — the `/payroll/pay-rules` screen.
 *
 * <p>Lets a non-engineer compose a {@code PayRule} SpEL formula with a palette of
 * engine-supported functions/variables (loaded from
 * `GET /api/payroll/pay-rules/formula-metadata`), validate it live
 * (`POST /validate-formula`), and save it onto a pay component
 * (`POST /api/payroll/pay-rules`). The palette is driven by the real engine so it
 * can never offer a function/variable the backend would reject.
 */
@Component({
  selector: 'hum-pay-rule-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    InputComponent,
    TextareaComponent,
    SelectComponent,
    ButtonComponent,
    AlertComponent,
    BadgeComponent,
    SkeletonRowComponent,
    SwitchComponent,
  ],
  templateUrl: './pay-rule-editor.component.html',
})
export default class PayRuleEditorComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PayRuleService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly validating = signal(false);
  protected readonly saving = signal(false);

  protected readonly metadata = signal<FormulaMetadata | null>(null);
  protected readonly validation = signal<FormulaValidationResult | null>(null);
  protected readonly activeRules = signal<PayRuleSummary[]>([]);

  private readonly formulaLen = signal(0);
  private readonly components = signal<PayComponent[]>([]);
  protected readonly componentOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.payRules.selectComponent') },
    ...this.components().map(c => ({ value: c.id, label: `${c.code} — ${c.name}` })),
  ]);

  protected readonly form = this.fb.nonNullable.group({
    payComponentId: ['', Validators.required],
    formula: ['', [Validators.required, Validators.maxLength(2000)]],
    priority: [''],
    effectiveFrom: [''],
    effectiveTo: [''],
    baseFormulaRef: [''],
    active: [true],
  });

  /** Live character budget against the engine's max formula length. */
  protected readonly remaining = computed(() => {
    const max = this.metadata()?.maxFormulaLength ?? 2000;
    return max - this.formulaLen();
  });

  constructor() {
    this.load();
    // Re-validation feedback is stale once the formula changes — clear it and track length.
    this.form.controls.formula.valueChanges.subscribe(v => {
      this.validation.set(null);
      this.formulaLen.set(v.length);
    });
    this.form.controls.payComponentId.valueChanges.subscribe(id => this.loadActiveRules(id));
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.formulaMetadata().subscribe({
      next: m => {
        this.metadata.set(m);
        this.form.controls.formula.setValidators([Validators.required, Validators.maxLength(m.maxFormulaLength)]);
        this.form.controls.formula.updateValueAndValidity({ emitEvent: false });
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
    this.service.listComponents().subscribe({
      next: c => {
        this.components.set(c);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  private loadActiveRules(componentId: string): void {
    if (!componentId) {
      this.activeRules.set([]);
      return;
    }
    this.service.activeRules(componentId).subscribe({
      next: r => this.activeRules.set(r),
      error: () => this.activeRules.set([]),
    });
  }

  /** Append a palette token to the formula (functions get `()`, variables/constants get `#`). */
  protected insert(token: string, kind: 'function' | 'variable' | 'constant'): void {
    const snippet = kind === 'function' ? `${token}()` : `#${token}`;
    const current = this.form.controls.formula.value;
    const sep = current.length && !/\s$/.test(current) ? ' ' : '';
    this.form.controls.formula.setValue(`${current}${sep}${snippet}`);
    this.form.controls.formula.markAsDirty();
  }

  protected signature(fn: FunctionMeta): string {
    return `${fn.name}(${fn.parameterTypes.join(', ')})`;
  }

  protected validate(): void {
    const formula = this.form.controls.formula.value.trim();
    if (!formula) {
      this.form.controls.formula.markAsTouched();
      return;
    }
    this.validating.set(true);
    this.validation.set(null);
    this.service.validateFormula(formula).subscribe({
      next: res => {
        this.validation.set(res);
        this.validating.set(false);
      },
      error: (err: unknown) => {
        // The backend rejects an invalid formula with 400; surface its message.
        this.validation.set({ formula, valid: false, error: normalizeHttpError(err) });
        this.validating.set(false);
      },
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: CreatePayRuleRequest = {
      payComponentId: raw.payComponentId,
      formula: raw.formula.trim(),
      active: raw.active,
      ...(raw.priority ? { priority: Number(raw.priority) } : {}),
      ...(raw.effectiveFrom ? { effectiveFrom: raw.effectiveFrom } : {}),
      ...(raw.effectiveTo ? { effectiveTo: raw.effectiveTo } : {}),
      ...(raw.baseFormulaRef.trim() ? { baseFormulaRef: raw.baseFormulaRef.trim() } : {}),
    };

    this.saving.set(true);
    this.service.createRule(body).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.payRules.saved'));
        this.saving.set(false);
        this.validation.set(null);
        this.form.controls.formula.reset('');
        this.loadActiveRules(raw.payComponentId);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }
}
