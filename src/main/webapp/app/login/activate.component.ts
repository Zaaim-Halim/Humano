import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AlertComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

/** Account activation — `GET /api/activate?key=`. `key` is bound from the query param. */
@Component({
  selector: 'hum-activate',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, LucideAngularModule, AlertComponent],
  templateUrl: './activate.component.html',
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
