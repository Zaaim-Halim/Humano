import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { ThemeService } from 'app/core/theme/theme.service';
import { IconButtonComponent } from 'app/shared/ui';

/**
 * AuthLayoutComponent — shared chrome for the public auth screens (sign in,
 * sign up, forgot/reset password, activate). Split-screen: a scrollable form
 * column on the left hosting the routed screen in `<router-outlet>`, and a
 * fixed indigo brand panel on the right (hidden below 880px). The screens
 * themselves stay wired to their JHipster services; this only supplies the
 * surrounding layout, top bar (wordmark + theme toggle) and footer.
 */
@Component({
  selector: 'hum-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, TranslatePipe, LucideAngularModule, IconButtonComponent],
  template: `
    <div class="hum-auth">
      <!-- Form side -->
      <div class="hum-auth__form-side">
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
          <div class="hum-auth__col">
            <router-outlet />
          </div>
        </main>

        <footer class="hum-auth__foot">
          <lucide-icon name="shield-check" [size]="14" aria-hidden="true" />
          <span>SOC 2 Type II · Encrypted in transit &amp; at rest</span>
        </footer>
      </div>

      <!-- Brand side -->
      <aside class="hum-auth__brand-side" aria-hidden="true">
        <div class="hum-auth__brand-grid"></div>
        <div class="hum-auth__brand-inner">
          <div class="hum-auth__wordmark hum-auth__wordmark--brand">humano<span class="hum-auth__dot" aria-hidden="true"></span></div>

          <div class="hum-auth__pitch">
            <h2 class="hum-auth__pitch-title">The calm system of record for HR &amp; payroll.</h2>
            <ul class="hum-auth__points">
              @for (point of points; track point.title) {
                <li class="hum-auth__point">
                  <span class="hum-auth__point-icon"><lucide-icon [name]="point.icon" [size]="18" /></span>
                  <span>
                    <span class="hum-auth__point-title">{{ point.title }}</span>
                    <span class="hum-auth__point-desc">{{ point.desc }}</span>
                  </span>
                </li>
              }
            </ul>
          </div>

          <figure class="hum-auth__quote">
            <blockquote>“Humano replaced three tools and a spreadsheet. Payroll went from two days to two hours.”</blockquote>
            <figcaption>
              <span class="hum-auth__quote-avatar">RA</span>
              <span>
                <span class="hum-auth__quote-name">Renata Alves</span>
                <span class="hum-auth__quote-role">Head of People · Lumen Labs</span>
              </span>
            </figcaption>
          </figure>
        </div>
      </aside>
    </div>
  `,
})
export default class AuthLayoutComponent {
  protected readonly theme = inject(ThemeService);

  protected readonly points = [
    { icon: 'wallet', title: 'Payroll', desc: 'Multi-country runs, automated taxes and payslips.' },
    { icon: 'check-check', title: 'Approvals', desc: 'Time off and expenses, routed and auditable.' },
    { icon: 'network', title: 'Source of truth', desc: 'One directory every team trusts.' },
  ];
}
