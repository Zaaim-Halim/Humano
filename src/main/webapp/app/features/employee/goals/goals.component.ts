import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, forkJoin } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { BadgeTone } from 'app/config/status-map';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  ProgressComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { Goal, GoalCheckIn, GoalStatus, GoalTeamMember, SaveGoalRequest } from '../models/goal.model';
import { EmployeeService } from '../services/employee.service';
import { GoalService } from '../services/goal.service';

type View = 'mine' | 'team';

/** The goal being added or edited; `id` empty for a new one, `employeeId` set when it is for a report. */
interface GoalDraft {
  id: string;
  employeeId: string;
  employeeName: string;
  title: string;
  description: string;
  successMeasure: string;
  weight: string;
  startDate: string;
  dueDate: string;
}

/** One person's goals, with the total weight of those still open. */
interface GoalGroup {
  employeeId: string;
  employeeName: string;
  goals: Goal[];
  /** Total weight of open goals when any has one; `null` otherwise. */
  weight: number | null;
}

const OPEN: GoalStatus[] = ['DRAFT', 'ACTIVE'];
const STATUS_TONE: Record<GoalStatus, BadgeTone> = { DRAFT: 'warning', ACTIVE: 'brand', COMPLETED: 'success', CANCELLED: 'neutral' };

/**
 * Goals — `/goals`: what each person aims to achieve this period, agreed with their manager.
 *
 * - **My goals**: propose a goal (it waits for the manager to agree), edit or delete it until then,
 *   record progress with check-ins and mark agreed goals complete.
 * - **My team** (managers): direct reports' goals — agree proposals, set goals (agreed at once),
 *   edit, record progress, complete or cancel them with a reason.
 *
 * Weights are optional; when used, a person's open goals should add up to 100 — flagged, never
 * blocked. A goal rated in a submitted review is locked. The review whose period covers a goal's
 * due date rates how far it was achieved; employees never rate their own goals. Each goal carries
 * what the signed-in user may do with it, so the page shows only allowed actions.
 */
@Component({
  selector: 'hum-goals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AutocompleteComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    InputComponent,
    ProgressComponent,
    SelectComponent,
    SkeletonComponent,
    SwitchComponent,
    TabsComponent,
    TextareaComponent,
  ],
  templateUrl: './goals.component.html',
})
export default class GoalsComponent {
  private readonly goalService = inject(GoalService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  /** HR setting goals for anyone (not only direct reports). */
  protected readonly canManageAll = inject(AccountService).hasPermission(Permission.MANAGE_PERFORMANCE_REVIEWS);

  protected readonly mine = signal<Goal[] | null>(null);
  protected readonly team = signal<Goal[] | null>(null);
  protected readonly members = signal<GoalTeamMember[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly view = signal<View>('mine');
  protected readonly showClosed = signal(false);

  /** Managers (people with direct reports) and HR see their team's tab. */
  protected readonly hasTeam = computed(() => this.canManageAll || this.members().length > 0 || !!this.team()?.length);
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'mine', label: this.label('humano.goals.tab.mine'), count: this.openCount(this.mine()) },
    ...(this.hasTeam() ? [{ id: 'team', label: this.label('humano.goals.tab.team'), count: this.openCount(this.team()) }] : []),
  ]);
  protected readonly memberOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.label('humano.goals.pickEmployee') },
    ...this.members().map(m => ({ value: m.id, label: m.name })),
  ]);
  protected readonly groups = computed<GoalGroup[]>(() => {
    const goals = (this.view() === 'mine' ? this.mine() : this.team()) ?? [];
    const byPerson = new Map<string, GoalGroup>();
    for (const g of goals) {
      const group = byPerson.get(g.employeeId) ?? { employeeId: g.employeeId, employeeName: g.employeeName, goals: [], weight: null };
      group.goals.push(g);
      byPerson.set(g.employeeId, group);
    }
    return [...byPerson.values()]
      .map(group => {
        const open = group.goals.filter(g => OPEN.includes(g.status));
        const weighted = open.filter(g => g.weight != null);
        return {
          ...group,
          goals: this.showClosed() ? group.goals : open,
          weight: weighted.length ? weighted.reduce((sum, g) => sum + (g.weight ?? 0), 0) : null,
        };
      })
      .filter(group => group.goals.length)
      .sort((a, b) => a.employeeName.localeCompare(b.employeeName));
  });
  protected readonly closedCount = computed(
    () => ((this.view() === 'mine' ? this.mine() : this.team()) ?? []).filter(g => !OPEN.includes(g.status)).length,
  );

  // Drawer and dialogs.
  protected readonly draft = signal<GoalDraft | null>(null);
  protected readonly tried = signal(false);
  protected readonly checkingIn = signal<Goal | null>(null);
  protected readonly checkInProgress = signal('');
  protected readonly checkInComment = signal('');
  protected readonly history = signal<GoalCheckIn[] | null>(null);
  protected readonly cancelling = signal<Goal | null>(null);
  protected readonly cancelReason = signal('');
  protected readonly deleting = signal<Goal | null>(null);

  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin([this.goalService.mine(), this.goalService.team(), this.goalService.teamMembers()]).subscribe({
      next: ([mine, team, members]) => {
        this.mine.set(mine);
        this.team.set(team);
        this.members.set(members);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected setView(value: string | undefined): void {
    this.view.set(value === 'team' ? 'team' : 'mine');
  }

  protected statusTone(status: GoalStatus): BadgeTone {
    return STATUS_TONE[status];
  }

  protected overdue(g: Goal): boolean {
    return OPEN.includes(g.status) && g.dueDate < new Date().toISOString().slice(0, 10);
  }

  // ------------------------------------------------------------ add / edit

  protected newGoal(): void {
    const today = new Date().toISOString().slice(0, 10);
    this.tried.set(false);
    this.draft.set({
      id: '',
      employeeId: '',
      employeeName: '',
      title: '',
      description: '',
      successMeasure: '',
      weight: '',
      startDate: today,
      dueDate: `${today.slice(0, 4)}-12-31`,
    });
  }

  protected edit(g: Goal): void {
    this.tried.set(false);
    this.draft.set({
      id: g.id,
      employeeId: g.employeeId,
      employeeName: g.employeeName,
      title: g.title,
      description: g.description ?? '',
      successMeasure: g.successMeasure ?? '',
      weight: g.weight != null ? String(g.weight) : '',
      startDate: g.startDate,
      dueDate: g.dueDate,
    });
  }

  /** The i18n key of what's wrong with the draft, or `null`. */
  protected problem(d: GoalDraft): string | null {
    if (!d.title.trim()) return 'humano.goals.error.title';
    if (this.view() === 'team' && !d.id && !d.employeeId) return 'humano.goals.error.employee';
    if (!d.startDate || !d.dueDate) return 'humano.goals.error.dates';
    if (d.dueDate < d.startDate) return 'humano.goals.error.dueBeforeStart';
    const w = d.weight.trim();
    if (w && !/^\d{1,3}$/.test(w)) return 'humano.goals.error.weight';
    if (w && Number(w) > 100) return 'humano.goals.error.weight';
    return null;
  }

  protected save(): void {
    const d = this.draft();
    this.tried.set(true);
    if (!d || this.problem(d)) return;
    const body: SaveGoalRequest = {
      ...(this.view() === 'team' && d.employeeId ? { employeeId: d.employeeId } : {}),
      title: d.title.trim(),
      description: d.description.trim() || null,
      successMeasure: d.successMeasure.trim() || null,
      weight: d.weight.trim() ? Number(d.weight) : null,
      startDate: d.startDate,
      dueDate: d.dueDate,
    };
    const ownProposal = !d.id && this.view() === 'mine';
    this.act(d.id ? this.goalService.update(d.id, body) : this.goalService.create(body), () => {
      this.draft.set(null);
      this.toast.success(this.label(ownProposal ? 'humano.goals.proposed' : 'humano.goals.saved', { title: body.title }));
    });
  }

  // ------------------------------------------------------------ actions

  protected agree(g: Goal): void {
    this.act(this.goalService.agree(g.id), () => this.toast.success(this.label('humano.goals.agreed', { title: g.title })));
  }

  protected complete(g: Goal): void {
    this.act(this.goalService.complete(g.id), () => this.toast.success(this.label('humano.goals.completed', { title: g.title })));
  }

  protected openCheckIn(g: Goal): void {
    this.checkingIn.set(g);
    this.checkInProgress.set(String(g.progress));
    this.checkInComment.set('');
    this.history.set(null);
    this.goalService.checkIns(g.id).subscribe({
      next: list => this.history.set(list),
      error: () => this.history.set([]),
    });
  }

  protected checkInInvalid(): boolean {
    const p = this.checkInProgress().trim();
    return !/^\d{1,3}$/.test(p) || Number(p) > 100;
  }

  protected saveCheckIn(): void {
    const g = this.checkingIn();
    if (!g || this.checkInInvalid()) return;
    this.act(
      this.goalService.checkIn(g.id, { progress: Number(this.checkInProgress()), comment: this.checkInComment().trim() || null }),
      () => {
        this.checkingIn.set(null);
        this.toast.success(this.label('humano.goals.checkedIn'));
      },
    );
  }

  protected openCancel(g: Goal): void {
    this.cancelReason.set('');
    this.cancelling.set(g);
  }

  protected confirmCancel(): void {
    const g = this.cancelling();
    const reason = this.cancelReason().trim();
    if (!g || !reason) return;
    this.act(this.goalService.cancel(g.id, reason), () => {
      this.cancelling.set(null);
      this.toast.success(this.label('humano.goals.cancelled', { title: g.title }));
    });
  }

  protected confirmDelete(): void {
    const g = this.deleting();
    if (!g) return;
    this.act(this.goalService.delete(g.id), () => {
      this.deleting.set(null);
      this.toast.success(this.label('humano.goals.deleted', { title: g.title }));
    });
  }

  /** Runs a change, then reloads both lists (a goal can appear in both). */
  private act(request$: Observable<unknown>, done: () => void): void {
    this.saving.set(true);
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        done();
        this.load();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private openCount(goals: Goal[] | null): number | undefined {
    return goals ? goals.filter(g => OPEN.includes(g.status)).length : undefined;
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
