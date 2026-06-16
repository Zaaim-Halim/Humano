import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

type Level = 'weak' | 'fair' | 'good' | 'strong';

const LEVELS: Record<number, { level: Level; key: string }> = {
  1: { level: 'weak', key: 'register.strength.weak' },
  2: { level: 'fair', key: 'register.strength.fair' },
  3: { level: 'good', key: 'register.strength.good' },
  4: { level: 'strong', key: 'register.strength.strong' },
};

/**
 * PasswordStrength — live 4-segment strength meter for sign-up / reset.
 * Score = (length ≥ 8) + (has upper & lower) + (has digit) + (has symbol).
 * Empty input shows the hint text instead of a level label.
 */
@Component({
  selector: 'hum-password-strength',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  host: { class: 'hum-strength' },
  template: `
    <div class="hum-strength__bars" aria-hidden="true">
      @for (i of [0, 1, 2, 3]; track i) {
        <span class="hum-strength__bar" [class.hum-strength__bar--on]="i < score()" [class]="'is-' + level()"></span>
      }
    </div>
    @if (score() > 0) {
      <span class="hum-strength__label" [class]="'is-' + level()" role="status">
        {{ 'register.strength.label' | translate }} <strong>{{ labelKey() | translate }}</strong>
      </span>
    } @else {
      <span class="hum-strength__hint hum-strength__label">{{ 'register.strength.hint' | translate }}</span>
    }
  `,
})
export class PasswordStrengthComponent {
  readonly value = input('');

  protected readonly score = computed(() => {
    const v = this.value();
    if (!v) return 0;
    let s = 0;
    if (v.length >= 8) s++;
    if (/[a-z]/.test(v) && /[A-Z]/.test(v)) s++;
    if (/\d/.test(v)) s++;
    if (/[^A-Za-z0-9]/.test(v)) s++;
    return s;
  });

  protected readonly level = computed<Level>(() => LEVELS[this.score()]?.level ?? 'weak');
  protected readonly labelKey = computed(() => LEVELS[this.score()]?.key ?? 'register.strength.weak');
}
