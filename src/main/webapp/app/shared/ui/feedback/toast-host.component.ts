import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { ToastComponent } from './alert.component';
import { ToastService } from './toast.service';

/**
 * ToastHost — fixed bottom-right stack that renders the `ToastService` queue.
 * Mount once near the app root. `dismissLabel` is the accessible label for the
 * dismiss control (pass a translated string).
 */
@Component({
  selector: 'hum-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ToastComponent],
  host: {
    'aria-live': 'polite',
    style:
      'position:fixed;bottom:var(--space-5);right:var(--space-5);z-index:80;display:flex;flex-direction:column;gap:var(--space-2_5);max-width:380px',
  },
  template: `
    @for (t of toasts.toasts(); track t.id) {
      <hum-toast
        [tone]="t.tone"
        [title]="t.title"
        [description]="t.description"
        [dismissLabel]="dismissLabel()"
        (closed)="toasts.dismiss(t.id)"
      />
    }
  `,
})
export class ToastHostComponent {
  readonly dismissLabel = input('Dismiss');
  protected readonly toasts = inject(ToastService);
}
