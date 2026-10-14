/** A CSV cell: text is quoted when it contains a delimiter, quote or line break; null is empty. */
function cell(value: unknown): string {
  if (value === null || value === undefined) return '';
  const text = String(value);
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** Builds a CSV document (header row + rows), with a BOM so spreadsheets read UTF-8 correctly. */
export function toCsv(header: string[], rows: unknown[][]): string {
  return '﻿' + [header, ...rows].map(r => r.map(cell).join(',')).join('\r\n');
}

/** Saves a CSV document as a file in the browser. */
export function downloadCsv(fileName: string, header: string[], rows: unknown[][]): void {
  const blob = new Blob([toCsv(header, rows)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName.endsWith('.csv') ? fileName : `${fileName}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
