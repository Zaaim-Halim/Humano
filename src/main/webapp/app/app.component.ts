import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import locale from '@angular/common/locales/en';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { ToastHostComponent } from 'app/shared/ui';

@Component({
  selector: 'hum-app',
  template: `
    <router-outlet />
    <hum-toast-host />
  `,
  imports: [RouterOutlet, ToastHostComponent],
})
export default class AppComponent {
  // Injected so the appearance effect (data-theme/density/chrome) is active app-wide.
  private readonly theme = inject(ThemeService);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  constructor() {
    this.applicationConfigService.setEndpointPrefix(SERVER_API_URL);
    registerLocaleData(locale);
  }
}
