import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  IconButtonComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { OnboardingChecklist, OnboardingService, OnboardingTaskLink, OnboardingTaskOwner } from '../index';

/** Quick picks for the default onboarding length. */
const LENGTH_PRESETS = [7, 14, 30];
const OWNERS: OnboardingTaskOwner[] = ['NEW_HIRE', 'MANAGER', 'HR'];
const LINKS: OnboardingTaskLink[] = ['ACCOUNT', 'DOCUMENTS', 'BENEFITS', 'TRAININGS'];

/** One editable checklist row; `key` keeps row identity stable while reordering. */
interface Row {
  key: number;
  title: string;
  description: string;
  dueDay: number;
  owner: OnboardingTaskOwner;
  /** '' = not linked. */
  link: OnboardingTaskLink | '';
}

/**
 * Onboarding checklist — `/onboarding/checklist` (`MANAGE_ONBOARDING`): the company's default
 * onboarding length and the tasks every new onboarding starts from.
 *
 * The default length is the finish-by date a new onboarding gets unless another is picked, and
 * the scale the tasks' due days are written on: a task due on day 3 of 30 lands on day 1 of a
 * 10-day onboarding. A track previews where the tasks fall. Tasks are edited in place (title,
 * description, owner — new hire, manager or HR & IT — due day, and optionally the employee record
 * that shows it's done: account, documents, benefits, trainings), reordered with up/down and
 * saved together; onboardings already started keep their own checklist.
 */
@Component({
  selector: 'hum-onboarding-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    ButtonComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    AlertComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './onboarding-checklist.component.html',
})
export default class OnboardingChecklistComponent {
  private readonly onboardingService = inject(OnboardingService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly presets = LENGTH_PRESETS;
  protected readonly ownerOptions: SelectOption[] = OWNERS.map(o => ({
    value: o,
    label: this.label(`humano.onboardingChecklist.owner.${o}`),
  }));
  protected readonly linkOptions: SelectOption[] = [
    { value: '', label: this.label('humano.onboardingChecklist.linkNone') },
    ...LINKS.map(l => ({ value: l, label: this.label(`humano.onboarding.linkRecord.${l}`) })),
  ];

  protected readonly loaded = signal<OnboardingChecklist | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly defaultDays = signal(30);
  protected readonly rows = signal<Row[]>([]);
  private nextKey = 0;
  /** Serialized state at load/save, to tell whether anything changed. */
  private readonly baseline = signal('');

  protected readonly dirty = computed(() => this.serialize() !== this.baseline());
  protected readonly lengthInvalid = computed(() => {
    const d = this.defaultDays();
    return !Number.isInteger(d) || d < 1 || d > 365;
  });
  protected readonly rowErrors = computed(() =>
    this.rows().map(r => ({
      title: !r.title.trim(),
      dueDay: !Number.isInteger(r.dueDay) || r.dueDay < 0 || r.dueDay > this.defaultDays(),
    })),
  );
  protected readonly valid = computed(
    () => !this.lengthInvalid() && this.rows().length > 0 && this.rowErrors().every(e => !e.title && !e.dueDay),
  );
  /** Track preview: one marker per due day, tasks sharing a day share a marker. */
  protected readonly markers = computed(() => {
    const total = Math.max(1, this.defaultDays());
    const byDay = new Map<number, string[]>();
    for (const r of this.rows()) {
      const day = Math.min(total, Math.max(0, Math.round(r.dueDay) || 0));
      byDay.set(day, [...(byDay.get(day) ?? []), r.title || this.label('humano.onboardingChecklist.untitled')]);
    }
    return [...byDay.entries()].map(([day, titles]) => ({
      day,
      pct: (day / total) * 100,
      count: titles.length,
      titles: titles.join(', '),
    }));
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.onboardingService.checklist().subscribe({
      next: c => this.apply(c),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected setLength(days: number): void {
    this.defaultDays.set(days);
  }

  protected update(key: number, patch: Partial<Row>): void {
    this.rows.update(rows => rows.map(r => (r.key === key ? { ...r, ...patch } : r)));
  }

  protected move(index: number, by: -1 | 1): void {
    this.rows.update(rows => {
      const next = [...rows];
      const [row] = next.splice(index, 1);
      next.splice(index + by, 0, row);
      return next;
    });
  }

  protected remove(key: number): void {
    this.rows.update(rows => rows.filter(r => r.key !== key));
  }

  protected add(): void {
    this.rows.update(rows => [
      ...rows,
      { key: this.nextKey++, title: '', description: '', dueDay: this.defaultDays(), owner: 'NEW_HIRE', link: '' },
    ]);
  }

  protected discard(): void {
    const c = this.loaded();
    if (c) this.apply(c);
  }

  protected save(): void {
    if (!this.valid()) return;
    this.saving.set(true);
    this.onboardingService
      .saveChecklist({
        defaultDays: this.defaultDays(),
        tasks: this.rows().map(r => ({
          title: r.title.trim(),
          description: r.description.trim() || null,
          dueDay: r.dueDay,
          owner: r.owner,
          link: r.link || null,
        })),
      })
      .subscribe({
        next: c => {
          this.apply(c);
          this.saving.set(false);
          this.toast.success(this.label('humano.onboardingChecklist.saved'));
        },
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.saving.set(false);
        },
      });
  }

  private apply(c: OnboardingChecklist): void {
    this.loaded.set(c);
    this.defaultDays.set(c.defaultDays);
    this.rows.set(
      c.tasks.map(t => ({
        key: this.nextKey++,
        title: t.title,
        description: t.description ?? '',
        dueDay: t.dueDay,
        owner: t.owner,
        link: t.link ?? '',
      })),
    );
    this.baseline.set(this.serialize());
  }

  private serialize(): string {
    return JSON.stringify([this.defaultDays(), this.rows().map(({ key: _key, ...r }) => r)]);
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
