import { DestroyRef, Signal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';

import { normalizeHttpError } from './http-error';
import { Page, PageRequest, defaultPageRequest } from './page.model';

export interface ListResource<T> {
  /** Current page rows (empty until first load resolves). */
  readonly items: Signal<T[]>;
  /** Full page envelope, or null before the first successful load. */
  readonly page: Signal<Page<T> | null>;
  readonly total: Signal<number>;
  readonly loading: Signal<boolean>;
  /** Inline error message for retry banners; null when healthy. */
  readonly error: Signal<string | null>;
  /** True once loaded and the result set is empty (drives the empty state). */
  readonly empty: Signal<boolean>;
  readonly params: Signal<PageRequest>;
  /** Merge a params patch (page/size/sort/filter) and refetch. */
  setParams(patch: Partial<PageRequest>): void;
  /** Refetch with the current params (retry button). */
  reload(): void;
}

export interface ListResourceOptions {
  /** Initial query params. */
  initial?: PageRequest;
  /** Fetch immediately on creation (default true); pass false to load after wiring route params. */
  autoLoad?: boolean;
}

/**
 * Signal-based container for a paged list endpoint. Owns loading/error/data
 * state so surfaces get explicit async states for free; cancels the in-flight
 * request when params change and tears down on the owner's destruction.
 *
 * Must be created in an injection context (component field initializer). The
 * `fetch` function decouples it from any specific service — pass
 * `req => this.employeeService.query(req)` or any `(PageRequest) => Page<T>`.
 * Does not toast (the error interceptors do); `error` is for inline retry only.
 *
 * NOTE: with the default `autoLoad`, the first fetch runs during field
 * initialization — declare any service the `fetch` closure references *before*
 * this field, or pass `{ autoLoad: false }` and call `reload()` after wiring
 * (e.g. once route params are read), otherwise the service is `undefined` at
 * first load.
 */
export function createListResource<T>(
  fetch: (req: PageRequest) => Observable<Page<T>>,
  options: ListResourceOptions = {},
): ListResource<T> {
  const destroyRef = inject(DestroyRef);
  const params = signal<PageRequest>({ ...defaultPageRequest(), ...options.initial });
  const page = signal<Page<T> | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  let inFlight: Subscription | null = null;

  const load = (): void => {
    loading.set(true);
    error.set(null);
    inFlight?.unsubscribe();
    inFlight = fetch(params())
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe({
        next: result => {
          page.set(result);
          loading.set(false);
        },
        error: (err: unknown) => {
          error.set(normalizeHttpError(err));
          loading.set(false);
        },
      });
  };

  if (options.autoLoad !== false) {
    load();
  }

  return {
    items: computed(() => page()?.content ?? []),
    page: page.asReadonly(),
    total: computed(() => page()?.totalElements ?? 0),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    empty: computed(() => !loading() && error() === null && (page()?.content.length ?? 0) === 0),
    params: params.asReadonly(),
    setParams: patch => {
      params.update(current => ({ ...current, ...patch }));
      load();
    },
    reload: load,
  };
}
