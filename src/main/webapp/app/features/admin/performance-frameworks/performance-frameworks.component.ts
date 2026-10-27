import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  COMPETENCY_CATEGORIES,
  Competency,
  CompetencyCategory,
  PerformanceFrameworkService,
  RATING_SCALE,
  REVIEW_QUESTION_AUDIENCES,
  ReviewTemplate,
  ReviewTemplateQuestion,
} from 'app/features/employee';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

type View = 'templates' | 'competencies';

/** The competency being added or edited; `id` empty for a new one. */
interface CompetencyDraft {
  id: string;
  name: string;
  description: string;
  category: CompetencyCategory;
  anchors: string[];
  active: boolean;
}

/** The template being added or edited; `id` empty for a new one. */
interface TemplateDraft {
  id: string;
  name: string;
  description: string;
  competencyIds: string[];
  questions: ReviewTemplateQuestion[];
  active: boolean;
}

/** Something to delete, waiting for confirmation. */
interface Deleting {
  kind: 'competency' | 'template';
  id: string;
  name: string;
}

const MAX_ITEMS = 20;

/**
 * Performance framework — `/performance-frameworks`: what reviews ask beyond the standard form.
 *
 * - **Competencies** (and company values), by category, with what each rating level looks like —
 *   the behaviour a reviewer should see to give a 1 … a 5.
 * - **Review templates**: the competencies the reviewer rates and the questions the employee and the
 *   reviewer answer, in order. A cycle picks a template while it is a draft and copies it when it
 *   opens, so editing here never changes a cycle that has started.
 *
 * Anything in use is retired rather than deleted: a retired competency stays on its templates, a
 * retired template on its cycles, but neither can be picked again. Read-only without
 * `MANAGE_PERFORMANCE_FRAMEWORKS`.
 */
@Component({
  selector: 'hum-performance-frameworks',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    DialogComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    SkeletonComponent,
    SwitchComponent,
    TabsComponent,
    TextareaComponent,
  ],
  templateUrl: './performance-frameworks.component.html',
})
export default class PerformanceFrameworksComponent {
  private readonly frameworkService = inject(PerformanceFrameworkService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly canManage = inject(AccountService).hasPermission(Permission.MANAGE_PERFORMANCE_FRAMEWORKS);
  protected readonly levels = RATING_SCALE;
  protected readonly maxItems = MAX_ITEMS;

  protected readonly competencies = signal<Competency[] | null>(null);
  protected readonly templates = signal<ReviewTemplate[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly view = signal<View>('templates');

  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'templates', label: this.label('humano.performance.framework.tab.templates'), count: this.templates()?.length },
    { id: 'competencies', label: this.label('humano.performance.framework.tab.competencies'), count: this.competencies()?.length },
  ]);
  /** Competencies by category, in the framework's category order. */
  protected readonly groups = computed(() =>
    COMPETENCY_CATEGORIES.map(category => ({
      category,
      items: (this.competencies() ?? []).filter(c => c.category === category),
    })).filter(g => g.items.length),
  );

  protected readonly categoryOptions: SelectOption[] = COMPETENCY_CATEGORIES.map(c => ({
    value: c,
    label: this.label(`humano.performance.framework.category.${c}`),
  }));
  protected readonly audienceOptions: SelectOption[] = REVIEW_QUESTION_AUDIENCES.map(a => ({
    value: a,
    label: this.label(`humano.performance.framework.audience.${a}`),
  }));

  // Drawers and confirmation.
  protected readonly competencyDraft = signal<CompetencyDraft | null>(null);
  protected readonly templateDraft = signal<TemplateDraft | null>(null);
  protected readonly deleting = signal<Deleting | null>(null);
  /** Shows the drafts' validation messages once saving was tried. */
  protected readonly tried = signal(false);
  /** The competency picked to add to the template being edited. */
  protected readonly toAdd = signal('');

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin([this.frameworkService.competencies(), this.frameworkService.templates()]).subscribe({
      next: ([competencies, templates]) => {
        this.competencies.set(competencies);
        this.templates.set(templates);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected setView(value: string | undefined): void {
    this.view.set(value === 'competencies' ? 'competencies' : 'templates');
  }

  // ------------------------------------------------------------ competencies

  protected newCompetency(): void {
    this.tried.set(false);
    this.competencyDraft.set({ id: '', name: '', description: '', category: 'CORE', anchors: ['', '', '', '', ''], active: true });
  }

  protected editCompetency(c: Competency): void {
    this.tried.set(false);
    this.competencyDraft.set({
      id: c.id,
      name: c.name,
      description: c.description ?? '',
      category: c.category,
      anchors: RATING_SCALE.map((_, i) => c.anchors[i] ?? ''),
      active: c.active,
    });
  }

  protected competencyNameMissing(d: CompetencyDraft): boolean {
    return !d.name.trim();
  }

  protected saveCompetency(): void {
    const d = this.competencyDraft();
    this.tried.set(true);
    if (!d || this.competencyNameMissing(d)) return;
    const body = {
      name: d.name.trim(),
      description: d.description.trim() || null,
      category: d.category,
      anchors: d.anchors.map(a => a.trim()),
      ...(d.id ? { active: d.active } : {}),
    };
    this.saving.set(true);
    (d.id ? this.frameworkService.updateCompetency(d.id, body) : this.frameworkService.createCompetency(body)).subscribe({
      next: saved => {
        this.saving.set(false);
        this.competencyDraft.set(null);
        this.toast.success(this.label('humano.performance.framework.competencySaved', { name: saved.name }));
        this.load();
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  /** Retire or restore without opening the form. */
  protected toggleCompetency(c: Competency): void {
    this.saving.set(true);
    this.frameworkService
      .updateCompetency(c.id, {
        name: c.name,
        description: c.description,
        category: c.category,
        anchors: c.anchors.map(a => a ?? ''),
        active: !c.active,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.load();
        },
        error: (err: unknown) => this.failed(err),
      });
  }

  // ------------------------------------------------------------ templates

  protected newTemplate(): void {
    this.tried.set(false);
    this.toAdd.set('');
    this.templateDraft.set({ id: '', name: '', description: '', competencyIds: [], questions: [], active: true });
  }

  protected editTemplate(t: ReviewTemplate): void {
    this.tried.set(false);
    this.toAdd.set('');
    this.templateDraft.set({
      id: t.id,
      name: t.name,
      description: t.description ?? '',
      competencyIds: t.competencies.map(c => c.id),
      questions: t.questions.map(q => ({ ...q, helpText: q.helpText ?? '' })),
      active: t.active,
    });
  }

  protected competencyName(id: string): string {
    return (this.competencies() ?? []).find(c => c.id === id)?.name ?? '';
  }

  protected isRetired(id: string): boolean {
    return (this.competencies() ?? []).find(c => c.id === id)?.active === false;
  }

  /** Active competencies the template doesn't have yet. */
  protected addOptions(d: TemplateDraft): SelectOption[] {
    return [
      { value: '', label: this.label('humano.performance.framework.template.pickCompetency') },
      ...(this.competencies() ?? [])
        .filter(c => c.active && !d.competencyIds.includes(c.id))
        .map(c => ({ value: c.id, label: `${c.name} · ${this.label(`humano.performance.framework.category.${c.category}`)}` })),
    ];
  }

  protected addCompetency(d: TemplateDraft, id: string): void {
    if (id && !d.competencyIds.includes(id) && d.competencyIds.length < MAX_ITEMS) {
      d.competencyIds = [...d.competencyIds, id];
    }
    this.toAdd.set('');
  }

  protected addQuestion(d: TemplateDraft, audience: ReviewTemplateQuestion['audience']): void {
    if (d.questions.length < MAX_ITEMS) {
      d.questions = [...d.questions, { audience, text: '', helpText: '', required: false }];
    }
  }

  protected move<T>(list: T[], index: number, by: -1 | 1): T[] {
    const to = index + by;
    if (to < 0 || to >= list.length) return list;
    const copy = [...list];
    [copy[index], copy[to]] = [copy[to], copy[index]];
    return copy;
  }

  protected without<T>(list: T[], index: number): T[] {
    return list.filter((_, i) => i !== index);
  }

  protected templateProblem(d: TemplateDraft): string | null {
    if (!d.name.trim()) return 'humano.performance.framework.error.name';
    if (!d.competencyIds.length && !d.questions.length) return 'humano.performance.framework.error.empty';
    if (d.questions.some(q => !q.text.trim())) return 'humano.performance.framework.error.questionText';
    return null;
  }

  protected saveTemplate(): void {
    const d = this.templateDraft();
    this.tried.set(true);
    if (!d || this.templateProblem(d)) return;
    const body = {
      name: d.name.trim(),
      description: d.description.trim() || null,
      competencyIds: d.competencyIds,
      questions: d.questions.map(q => ({ ...q, text: q.text.trim(), helpText: q.helpText?.trim() ?? null })),
      ...(d.id ? { active: d.active } : {}),
    };
    this.saving.set(true);
    (d.id ? this.frameworkService.updateTemplate(d.id, body) : this.frameworkService.createTemplate(body)).subscribe({
      next: saved => {
        this.saving.set(false);
        this.templateDraft.set(null);
        this.toast.success(this.label('humano.performance.framework.templateSaved', { name: saved.name }));
        this.load();
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  protected toggleTemplate(t: ReviewTemplate): void {
    this.saving.set(true);
    this.frameworkService
      .updateTemplate(t.id, {
        name: t.name,
        description: t.description,
        competencyIds: t.competencies.map(c => c.id),
        questions: t.questions,
        active: !t.active,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.load();
        },
        error: (err: unknown) => this.failed(err),
      });
  }

  protected count(t: ReviewTemplate, audience: ReviewTemplateQuestion['audience']): number {
    return t.questions.filter(q => q.audience === audience).length;
  }

  // ------------------------------------------------------------ delete

  protected confirmDelete(): void {
    const d = this.deleting();
    if (!d) return;
    this.saving.set(true);
    (d.kind === 'competency' ? this.frameworkService.deleteCompetency(d.id) : this.frameworkService.deleteTemplate(d.id)).subscribe({
      next: () => {
        this.saving.set(false);
        this.deleting.set(null);
        this.toast.success(this.label('humano.performance.framework.deleted', { name: d.name }));
        this.load();
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  private failed(err: unknown): void {
    this.saving.set(false);
    this.toast.danger(normalizeHttpError(err));
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
