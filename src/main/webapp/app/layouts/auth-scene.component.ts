import { ChangeDetectionStrategy, Component } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

/**
 * The quiet scene behind the account screens: everyday Humano moments around the form — a payslip
 * paid, time off approved, someone clocking in, a payroll run being posted, a team, a new hire.
 * Decorative only (hidden from assistive tech, and from narrow screens where it would sit under
 * the form). On load the cards appear one after another and play their one change (the run fills
 * and posts, the request is approved), then drift very slowly; all of it is still when reduced
 * motion is preferred. The people and figures are illustrative.
 */
@Component({
  selector: 'hum-auth-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: { class: 'hum-scene', 'aria-hidden': 'true' },
  template: `
    <!-- Payslip -->
    <div class="hum-scene__item" style="--i: 0; left: 6%; top: 13%">
      <div class="hum-scene__card hum-scene__float" style="--f: 0s">
        <div class="hum-scene__row">
          <span class="hum-scene__icon"><lucide-icon name="receipt" [size]="14" /></span>
          <span class="hum-scene__title">March payslip</span>
          <span class="hum-scene__badge is-success">Paid</span>
        </div>
        <div class="hum-scene__person">
          <span class="hum-scene__avatar" style="--hue: 262">AR</span>
          <span class="hum-scene__name">Ava Romano</span>
          <span class="hum-scene__amount">$5,420.00</span>
        </div>
      </div>
    </div>

    <!-- Payroll run -->
    <div class="hum-scene__item" style="--i: 2; left: 11%; top: 44%">
      <div class="hum-scene__card hum-scene__float" style="--f: -3s">
        <div class="hum-scene__row">
          <span class="hum-scene__icon"><lucide-icon name="wallet" [size]="14" /></span>
          <span class="hum-scene__title">March payroll run</span>
          <span class="hum-scene__swap">
            <span class="hum-scene__badge is-info hum-scene__swap-from">Calculating</span>
            <span class="hum-scene__badge is-success hum-scene__swap-to">Posted</span>
          </span>
        </div>
        <div class="hum-scene__meter"><span class="hum-scene__meter-fill"></span></div>
        <div class="hum-scene__sub">
          <span>312 employees</span>
          <span class="hum-scene__amount">$486,204</span>
        </div>
      </div>
    </div>

    <!-- Clock-in -->
    <div class="hum-scene__item" style="--i: 4; left: 7%; top: 74%">
      <div class="hum-scene__card hum-scene__card--slim hum-scene__float" style="--f: -6s">
        <span class="hum-scene__live"></span>
        <span class="hum-scene__avatar" style="--hue: 168">DS</span>
        <span class="hum-scene__text"><strong>Diego Santos</strong> clocked in</span>
        <span class="hum-scene__time"><lucide-icon name="timer" [size]="12" /> 08:58</span>
      </div>
    </div>

    <!-- Time off -->
    <div class="hum-scene__item" style="--i: 1; right: 6%; top: 12%">
      <div class="hum-scene__card hum-scene__float" style="--f: -2s">
        <div class="hum-scene__row">
          <span class="hum-scene__icon"><lucide-icon name="palmtree" [size]="14" /></span>
          <span class="hum-scene__title">Time off</span>
          <span class="hum-scene__swap">
            <span class="hum-scene__badge is-warning hum-scene__swap-from">Pending</span>
            <span class="hum-scene__badge is-success hum-scene__swap-to">Approved</span>
          </span>
        </div>
        <div class="hum-scene__person">
          <span class="hum-scene__avatar" style="--hue: 24">MC</span>
          <span class="hum-scene__name">Mei Chen</span>
          <span class="hum-scene__muted"><lucide-icon name="calendar" [size]="12" /> Jun 12–16</span>
        </div>
      </div>
    </div>

    <!-- Team -->
    <div class="hum-scene__item" style="--i: 3; right: 11%; top: 42%">
      <div class="hum-scene__card hum-scene__org hum-scene__float" style="--f: -5s">
        <div class="hum-scene__row">
          <span class="hum-scene__icon"><lucide-icon name="network" [size]="14" /></span>
          <span class="hum-scene__title">People team</span>
        </div>
        <svg class="hum-scene__links" viewBox="0 0 160 44" preserveAspectRatio="none">
          <path d="M80 4 V18 M24 18 H136 M24 18 V36 M80 18 V36 M136 18 V36" />
        </svg>
        <div class="hum-scene__org-lead"><span class="hum-scene__avatar" style="--hue: 228">RA</span></div>
        <div class="hum-scene__org-team">
          <span class="hum-scene__avatar" style="--hue: 330">JO</span>
          <span class="hum-scene__avatar" style="--hue: 190">SM</span>
          <span class="hum-scene__avatar" style="--hue: 45">OH</span>
        </div>
      </div>
    </div>

    <!-- New hire -->
    <div class="hum-scene__item" style="--i: 5; right: 7%; top: 73%">
      <div class="hum-scene__card hum-scene__card--slim hum-scene__float" style="--f: -8s">
        <span class="hum-scene__icon is-brand"><lucide-icon name="user-plus" [size]="14" /></span>
        <span class="hum-scene__text"><strong>Sofia Marin</strong> joins Monday</span>
        <span class="hum-scene__check"><lucide-icon name="check" [size]="12" /></span>
      </div>
    </div>
  `,
})
export class AuthSceneComponent {}
