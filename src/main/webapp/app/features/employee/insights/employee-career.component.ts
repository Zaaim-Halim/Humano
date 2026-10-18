import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { BadgeTone } from 'app/config/status-map';
import { PositionChange, PositionChangeRef, PositionChangeType } from 'app/features/admin/models/position-change.model';
import { PositionChangeService } from 'app/features/admin/services/position-change.service';
import { AlertComponent, BadgeComponent, ButtonComponent, SkeletonRowComponent } from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { MeWorkService } from '../services/me-work.service';

const TONES: Record<PositionChangeType, BadgeTone> = { PROMOTION: 'success', DEMOTION: 'warning', TRANSFER: 'info', OTHER: 'neutral' };

/** One "from → to" line of a change. */
interface Move {
  labelKey: string;
  from: string | null;
  to: string;
}

/**
 * Career history: someone's promotions, transfers and other position changes, newest first, with
 * what moved from what to what, from when and why. Shown to HR on the employee page and to the
 * person on My profile (`self`, via `/api/me`). Salaries appear only when the server includes them.
 * Bump `reloadKey` to refresh after recording a change.
 */
@Component({
  selector: 'hum-employee-career',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, LucideAngularModule, MoneyPipe, AlertComponent, BadgeComponent, ButtonComponent, SkeletonRowComponent],
  templateUrl: './employee-career.component.html',
})
export class EmployeeCareerComponent {
  readonly employeeId = input.required<string>();
  readonly self = input(false);
  readonly reloadKey = input(0);
  /** Currency the salaries are in (the employee's pay currency). */
  readonly currency = input<string | null>(null);

  protected readonly changes = signal<PositionChange[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly tones = TONES;

  private readonly positionChanges = inject(PositionChangeService);
  private readonly meWork = inject(MeWorkService);

  constructor() {
    effect(() => {
      const id = this.employeeId();
      this.reloadKey();
      if (id) untracked(() => this.load());
    });
  }

  protected load(): void {
    this.error.set(null);
    const request: Observable<PositionChange[]> = this.self()
      ? this.meWork.positionHistory()
      : this.positionChanges.history(this.employeeId());
    request.subscribe({
      next: list => this.changes.set(list),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  /** The parts of a change that moved, in a fixed order. */
  protected moves(c: PositionChange): Move[] {
    const moves: Move[] = [];
    const add = (labelKey: string, from: PositionChangeRef | null, to: PositionChangeRef | null): void => {
      if (to) moves.push({ labelKey, from: from?.name ?? null, to: to.name });
    };
    add('humano.positionChange.position', c.oldPosition, c.newPosition);
    add('humano.positionChange.department', c.oldDepartment, c.newDepartment);
    add('humano.positionChange.unit', c.oldUnit, c.newUnit);
    add('humano.positionChange.manager', c.oldManager, c.newManager);
    return moves;
  }
}
