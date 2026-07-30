import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

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
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';

import { PayRuleService } from '../services/pay-rule.service';
import { CreatePayRuleRequest, FormulaMetadata, FormulaValidationResult, PayComponent, PayRuleSummary } from '../models/pay-rule.model';
import {
  CONSTANT_DOCS,
  FUNCTION_CATEGORY_ICON,
  FUNCTION_CATEGORY_LABEL,
  FUNCTION_CATEGORY_ORDER,
  FUNCTION_DOCS,
  FunctionCategory,
  OPERATOR_GROUP_ORDER,
  OPERATORS,
  OperatorItem,
  RECIPES,
  Recipe,
  VARIABLE_DOCS,
  VARIABLE_GROUP_ORDER,
} from './formula-catalog';

/** An engine function, enriched with catalog docs for display + insertion. */
interface FnItem {
  name: string;
  signature: string;
  description: string;
  example?: string;
  returns?: string;
}
interface FnCategory {
  id: FunctionCategory;
  label: string;
  icon: string;
  items: FnItem[];
}
interface NamedItem {
  name: string;
  description: string;
}
interface VarGroup {
  label: string;
  items: NamedItem[];
}
interface OpGroup {
  label: string;
  items: OperatorItem[];
}
type RefTab = 'functions' | 'variables' | 'operators' | 'examples';

/**
 * Pay-rule formula editor (HR/admin) — the `/payroll/pay-rules` screen.
 *
 * <p>Lets a non-engineer compose a {@code PayRule} SpEL formula with a guided,
 * searchable reference of the engine's functions/variables/constants (loaded
 * from `GET /api/payroll/pay-rules/formula-metadata` and enriched by
 * `formula-catalog`), insert tokens at the cursor, validate live against sample
 * values (`POST /validate-formula`), and save the rule onto a pay component
 * (`POST /api/payroll/pay-rules`). The reference is driven by the real engine
 * contract, so it can never offer a function/variable the backend would reject.
 */
@Component({
  selector: 'hum-pay-rule-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    ButtonComponent,
    AlertComponent,
    BadgeComponent,
    SkeletonRowComponent,
    SwitchComponent,
    TabsComponent,
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

  /** Reference panel state. */
  protected readonly activeTab = signal<RefTab>('functions');
  protected readonly search = signal('');
  protected readonly recipes: Recipe[] = RECIPES;

  private readonly formulaField = viewChild<ElementRef<HTMLTextAreaElement>>('formulaField');
  // Caret remembered from the textarea. Palette buttons blur it before their click
  // fires, so we snapshot the selection on blur/keyup/mouseup and insert there.
  private lastSelStart: number | null = null;
  private lastSelEnd: number | null = null;

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

  private readonly normalizedSearch = computed(() => this.search().trim().toLowerCase());

  /** Functions grouped by category, enriched with docs, filtered by search. */
  protected readonly functionGroups = computed<FnCategory[]>(() => {
    const meta = this.metadata();
    if (!meta) return [];
    const q = this.normalizedSearch();
    const byCategory = new Map<FunctionCategory, FnItem[]>();

    for (const fn of meta.functions) {
      const doc = FUNCTION_DOCS[fn.name];
      const params = doc?.params ?? fn.parameterTypes;
      const item: FnItem = {
        name: fn.name,
        signature: `${fn.name}(${params.join(', ')})`,
        description: doc?.description ?? '',
        example: doc?.example,
        returns: doc?.returns,
      };
      if (q && !(item.name.toLowerCase().includes(q) || item.description.toLowerCase().includes(q))) continue;
      const cat = doc?.category ?? 'math';
      (byCategory.get(cat) ?? byCategory.set(cat, []).get(cat)!).push(item);
    }

    return FUNCTION_CATEGORY_ORDER.filter(c => byCategory.has(c)).map(c => ({
      id: c,
      label: FUNCTION_CATEGORY_LABEL[c],
      icon: FUNCTION_CATEGORY_ICON[c],
      items: byCategory.get(c)!,
    }));
  });

  /** Variables grouped, enriched, filtered by search. */
  protected readonly variableGroups = computed<VarGroup[]>(() => {
    const meta = this.metadata();
    if (!meta) return [];
    const q = this.normalizedSearch();
    const byGroup = new Map<string, NamedItem[]>();

    for (const name of meta.variables) {
      const doc = VARIABLE_DOCS[name];
      const item: NamedItem = { name, description: doc?.description ?? '' };
      if (q && !(name.toLowerCase().includes(q) || item.description.toLowerCase().includes(q))) continue;
      const group = doc?.group ?? 'other';
      (byGroup.get(group) ?? byGroup.set(group, []).get(group)!).push(item);
    }

    const groups: VarGroup[] = VARIABLE_GROUP_ORDER.filter(g => byGroup.has(g.id)).map(g => ({
      label: g.label,
      items: byGroup.get(g.id)!,
    }));

    // Constants are just named `#NAME` references, so they live with variables.
    const constants = meta.constants
      .map(name => ({ name, description: CONSTANT_DOCS[name] ?? '' }))
      .filter(i => !q || i.name.toLowerCase().includes(q) || i.description.toLowerCase().includes(q));
    if (constants.length) {
      groups.push({ label: this.translate.instant('humano.payRules.constants'), items: constants });
    }
    return groups;
  });

  protected readonly operatorGroups = computed<OpGroup[]>(() => {
    const q = this.normalizedSearch();
    return OPERATOR_GROUP_ORDER.map(g => ({
      label: g.label,
      items: OPERATORS.filter(
        op => op.group === g.id && (!q || op.symbol.toLowerCase().includes(q) || op.description.toLowerCase().includes(q)),
      ),
    })).filter(g => g.items.length > 0);
  });

  protected readonly filteredRecipes = computed<Recipe[]>(() => {
    const q = this.normalizedSearch();
    if (!q) return this.recipes;
    return this.recipes.filter(
      r => r.title.toLowerCase().includes(q) || r.description.toLowerCase().includes(q) || r.formula.toLowerCase().includes(q),
    );
  });

  /** Pattern hint for component-code variables the engine also accepts. */
  protected readonly dynamicHint = computed(() => this.metadata()?.dynamicVariablePattern ?? '');

  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'functions', label: this.translate.instant('humano.payRules.functions') },
    { id: 'variables', label: this.translate.instant('humano.payRules.variables') },
    { id: 'operators', label: this.translate.instant('humano.payRules.operators') },
    { id: 'examples', label: this.translate.instant('humano.payRules.tabExamples') },
  ]);

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

  /** Insert a function call (`#name()`, cursor between the parens) at the caret. */
  protected insertFunction(name: string): void {
    this.insertAtCursor(`#${name}()`, 1);
  }

  /** Insert a variable/constant reference (`#name`) at the caret. */
  protected insertVariable(name: string): void {
    this.insertAtCursor(`#${name}`, 0);
  }

  /** Insert an operator at the caret (raw text, no `#` prefix). */
  protected insertOperator(op: OperatorItem): void {
    this.insertAtCursor(op.insert, op.caretBack);
  }

  /** Snapshot the caret while the textarea is focused (before a palette click steals focus). */
  protected rememberSelection(): void {
    const el = this.formulaField()?.nativeElement;
    if (el) {
      this.lastSelStart = el.selectionStart;
      this.lastSelEnd = el.selectionEnd;
    }
  }

  /** Load a worked example, replacing the current draft (recipes are complete formulas). */
  protected useRecipe(formula: string): void {
    this.setFormula(formula);
  }

  /**
   * Insert `snippet` at the last-known caret (replacing any selection), leaving the
   * cursor `caretBack` chars from its end. Adds a leading space when it would butt up
   * against an existing token so the formula stays legible.
   */
  private insertAtCursor(snippet: string, caretBack: number): void {
    const value = this.form.controls.formula.value;
    const start = Math.min(this.lastSelStart ?? value.length, value.length);
    const end = Math.min(this.lastSelEnd ?? value.length, value.length);
    const before = value.slice(0, start);
    const after = value.slice(end);
    const needsSpace = /[\w)#]$/.test(before);
    const insert = (needsSpace ? ' ' : '') + snippet;
    const next = before + insert + after;
    const caret = before.length + insert.length - caretBack;
    this.form.controls.formula.setValue(next);
    this.form.controls.formula.markAsDirty();
    this.lastSelStart = this.lastSelEnd = caret;
    this.focusCaret(caret);
  }

  protected clearFormula(): void {
    this.setFormula('');
    this.validation.set(null);
  }

  private setFormula(text: string): void {
    this.form.controls.formula.setValue(text);
    this.form.controls.formula.markAsDirty();
    this.lastSelStart = this.lastSelEnd = text.length;
    this.focusCaret(text.length);
  }

  /** Focus the textarea and place the caret, after Angular writes the new value back. */
  private focusCaret(caret: number): void {
    setTimeout(() => {
      const node = this.formulaField()?.nativeElement;
      if (node) {
        node.focus();
        node.setSelectionRange(caret, caret);
      }
    });
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
        this.lastSelStart = this.lastSelEnd = null;
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
