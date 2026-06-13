import { Injectable, Signal, signal } from '@angular/core';

import { FeedbackTone } from './alert.component';

export interface ToastInstance {
  id: number;
  tone: FeedbackTone;
  /** Already-translated title. */
  title: string;
  /** Already-translated description. */
  description?: string;
}

export interface ToastOptions {
  tone?: FeedbackTone;
  title: string;
  description?: string;
  /** Auto-dismiss delay in ms; 0 keeps it until dismissed. Danger defaults to sticky. */
  timeout?: number;
}

/**
 * Signal-based toast stack. Replaces the JHipster `jhi-alert` notifier.
 * Render exactly one `<hum-toast-host>` near the app root; call `show(...)`
 * (or the tone helpers) from anywhere. Callers pass already-translated strings.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts: Signal<readonly ToastInstance[]>;

  private readonly _toasts = signal<ToastInstance[]>([]);
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private nextId = 0;

  constructor() {
    this.toasts = this._toasts.asReadonly();
  }

  show(options: ToastOptions): number {
    const id = this.nextId++;
    const tone = options.tone ?? 'info';
    this._toasts.update(list => [...list, { id, tone, title: options.title, description: options.description }]);

    const timeout = options.timeout ?? (tone === 'danger' ? 0 : 5000);
    if (timeout > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), timeout),
      );
    }
    return id;
  }

  success(title: string, description?: string): number {
    return this.show({ tone: 'success', title, description });
  }
  info(title: string, description?: string): number {
    return this.show({ tone: 'info', title, description });
  }
  warning(title: string, description?: string): number {
    return this.show({ tone: 'warning', title, description });
  }
  danger(title: string, description?: string): number {
    return this.show({ tone: 'danger', title, description });
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this._toasts.update(list => list.filter(t => t.id !== id));
  }
}
