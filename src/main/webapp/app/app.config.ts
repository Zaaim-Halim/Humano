import { ApplicationConfig, LOCALE_ID, importProvidersFrom, provideZonelessChangeDetection } from '@angular/core';
import { BrowserModule, Title } from '@angular/platform-browser';
import { RouterFeatures, TitleStrategy, provideRouter, withComponentInputBinding, withDebugTracing } from '@angular/router';
import { ServiceWorkerModule } from '@angular/service-worker';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { LucideAngularModule } from 'lucide-angular';

import './config/dayjs';
import { lucideIcons } from './config/lucide-icons';
import { TranslationModule } from 'app/shared/language/translation.module';
import { environment } from 'environments/environment';
import { httpInterceptorProviders } from './core/interceptor';
import routes from './app.routes';
import { AppPageTitleStrategy } from './app-page-title-strategy';

const routerFeatures: RouterFeatures[] = [withComponentInputBinding()];
if (environment.DEBUG_INFO_ENABLED) {
  routerFeatures.push(withDebugTracing());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes, ...routerFeatures),
    importProvidersFrom(BrowserModule),
    // Set this to true to enable service worker (PWA)
    importProvidersFrom(ServiceWorkerModule.register('ngsw-worker.js', { enabled: false })),
    importProvidersFrom(TranslationModule),
    importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
    provideHttpClient(withInterceptorsFromDi()),
    Title,
    { provide: LOCALE_ID, useValue: 'en' },
    httpInterceptorProviders,
    { provide: TitleStrategy, useClass: AppPageTitleStrategy },
  ],
};
