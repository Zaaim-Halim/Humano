import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { ThemeService } from 'app/core/theme/theme.service';
import { IconButtonComponent } from 'app/shared/ui';

import { AuthSceneComponent } from './auth-scene.component';

interface LedgerRow {
  /** Amount bars on the row: left offset and width in %, and whether it is a highlighted (brand) figure. */
  cells: { left: number; width: number; brand: boolean }[];
  /** Stagger of this row's fill, in ms. */
  delay: number;
}

const ROWS = 18;

/** Deterministic pseudo-random sequence, so the ledger looks the same on every visit. */
function sequence(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 9301 + 49297) % 233280;
    return s / 233280;
  };
}

/** A payroll register's rows: a name column, a couple of figures, and a total at the right edge. */
function ledger(): LedgerRow[] {
  const next = sequence(17);
  return Array.from({ length: ROWS }, (_, i) => ({
    delay: 120 + i * 45,
    cells: [
      { left: 4, width: 10 + next() * 10, brand: false },
      { left: 32 + next() * 4, width: 6 + next() * 8, brand: false },
      { left: 54 + next() * 4, width: 5 + next() * 7, brand: false },
      { left: 82, width: 6 + next() * 8, brand: i % 5 === 2 },
    ],
  }));
}

/**
 * AuthLayoutComponent — the shared frame of the public account screens (sign in, start a trial,
 * confirm a sign-up, reset a password, activate). One centred column on a quiet canvas: the routed
 * screen renders on a single surface, with the wordmark and theme toggle above and a small footer
 * below. Behind it, a faint payroll register fills in on load and a few everyday HR moments
 * ({@link AuthSceneComponent}) appear around the form; all still when reduced motion is preferred.
 */
@Component({
  selector: 'hum-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, TranslatePipe, LucideAngularModule, IconButtonComponent, AuthSceneComponent],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__ledger" aria-hidden="true">
        @for (row of rows; track $index) {
          <div class="hum-auth__ledger-row" [style.--d]="row.delay + 'ms'">
            @for (cell of row.cells; track $index) {
              <span
                class="hum-auth__ledger-cell"
                [class.is-brand]="cell.brand"
                [style.left.%]="cell.left"
                [style.width.%]="cell.width"
              ></span>
            }
          </div>
        }
      </div>

      <hum-auth-scene />

      <header class="hum-auth__bar">
        <a routerLink="/" class="hum-auth__wordmark">humano<span class="hum-auth__dot" aria-hidden="true"></span></a>
        <hum-icon-button
          [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'"
          [label]="'humano.shell.toggleTheme' | translate"
          variant="ghost"
          (click)="theme.toggleTheme()"
        />
      </header>

      <main class="hum-auth__main">
        <div class="hum-auth__panel">
          <router-outlet />
        </div>
      </main>

      <footer class="hum-auth__foot">
        <span class="hum-auth__foot-item"
          ><lucide-icon name="database" [size]="13" aria-hidden="true" />{{ 'login.footer.isolated' | translate }}</span
        >
        <span class="hum-auth__foot-links">
          <a routerLink="/legal/terms">{{ 'legal.links.terms' | translate }}</a>
          <a routerLink="/legal/privacy">{{ 'legal.links.privacy' | translate }}</a>
          <a routerLink="/legal/data-protection">{{ 'legal.links.dpa' | translate }}</a>
        </span>
        <span>© {{ year }} Humano</span>
      </footer>
    </div>
  `,
})
export default class AuthLayoutComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly rows = ledger();
  protected readonly year = new Date().getFullYear();
}
