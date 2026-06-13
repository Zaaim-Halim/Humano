import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';
import { LucideAngularModule } from 'lucide-angular';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
  selector: 'hum-app',
  template: `
    <main class="humano-shell grid gap-4 p-6">
      <h1 class="text-2xl text-strong">Humano</h1>
      <p class="text-default">Frontend rebuild in progress.</p>
      <div class="flex items-center gap-3">
        <button type="button" class="hum-btn hum-btn--primary">
          <lucide-icon name="check" [size]="16" />
          Get started
        </button>
        <button type="button" class="hum-btn hum-btn--ghost" (click)="theme.toggleTheme()">
          <lucide-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="16" />
          {{ theme.theme() === 'dark' ? 'Light' : 'Dark' }}
        </button>
      </div>
      <router-outlet />
    </main>
  `,
  imports: [RouterOutlet, LucideAngularModule],
})
export default class AppComponent {
  protected readonly theme = inject(ThemeService);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  constructor() {
    this.applicationConfigService.setEndpointPrefix(SERVER_API_URL);
    registerLocaleData(locale);
  }
}
