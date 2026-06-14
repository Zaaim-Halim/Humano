import { HttpErrorResponse } from '@angular/common/http';

/**
 * Extract a human-readable message from a failed request for an *inline* error
 * signal (retry banners, empty-error states). Global user-facing toasts are
 * already handled by `error-handler.interceptor` / `notification.interceptor` —
 * do not also toast from services or the user sees the error twice.
 *
 * Reads RFC 7807 problem+json (`detail`/`title`) which the backend's
 * `ExceptionTranslator` emits, then falls back to the transport message.
 */
export function normalizeHttpError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { detail?: string; title?: string; message?: string } | string | null;
    if (body && typeof body === 'object') {
      return body.detail ?? body.title ?? body.message ?? error.message;
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    return error.status ? `Request failed (${error.status})` : error.message;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}
