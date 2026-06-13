import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

@Component({
  selector: 'hum-app',
  template: `
    <main class="humano-shell">
      <h1>Humano</h1>
      <p>Frontend rebuild in progress.</p>
      <router-outlet />
    </main>
  `,
  imports: [RouterOutlet],
})
export default class AppComponent {
  private readonly applicationConfigService = inject(ApplicationConfigService);

  constructor() {
    this.applicationConfigService.setEndpointPrefix(SERVER_API_URL);
    registerLocaleData(locale);
  }
}
