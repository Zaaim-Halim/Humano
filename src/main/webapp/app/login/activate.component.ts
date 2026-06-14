import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

/** Account activation — `GET /api/activate?key=`. `key` is bound from the query param. */
@Component({
  selector: 'hum-activate',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, AlertComponent],
  template: `
    <div class="hum-auth">
      <div class="hum-auth__panel">
        <div class="hum-auth__brand">humano<span class="hum-side__dot" aria-hidden="true"></span></div>
        <h1 class="hum-auth__title">{{ 'activate.title' | translate }}</h1>

        @switch (status()) {
          @case ('success') {
            <hum-alert tone="success">
              <span [innerHTML]="'activate.messages.success' | translate"></span><a routerLink="/login">{{ 'login.title' | translate }}</a>
            </hum-alert>
          }
          @case ('error') {
            <hum-alert tone="danger"><span [innerHTML]="'activate.messages.error' | translate"></span></hum-alert>
            <div class="hum-auth__links">
              <a routerLink="/register">{{ 'register.title' | translate }}</a>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export default class ActivateComponent implements OnInit {
  /** Activation key from `?key=` (bound via `withComponentInputBinding()`). */
  readonly key = input<string>();

  private readonly api = inject(AuthPublicService);
  protected readonly status = signal<'pending' | 'success' | 'error'>('pending');

  ngOnInit(): void {
    const key = this.key();
    if (!key) {
      this.status.set('error');
      return;
    }
    this.api.activate(key).subscribe({
      next: () => this.status.set('success'),
      error: () => this.status.set('error'),
    });
  }
}
