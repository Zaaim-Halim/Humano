/**
 * Strip HTML tags from a translated string. Several JHipster i18n messages embed
 * `<strong>…</strong>` for inline alerts rendered via `[innerHTML]`; toasts show
 * plain text, so flatten the markup before passing a message to `ToastService`.
 */
export function stripHtml(value: string): string {
  return value.replace(/<[^>]*>/g, '').trim();
}
