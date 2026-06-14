import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, TemplateRef, booleanAttribute, computed, input, output, signal } from '@angular/core';

import { CheckboxComponent } from '../forms/checkbox.component';

export type Row = Record<string, unknown>;

export interface Column<T extends Row = Row> {
  key: string;
  label: string;
  numeric?: boolean;
  sortable?: boolean;
  width?: string | number;
  /** Custom cell template; context = { $implicit: row, value }. */
  cell?: TemplateRef<{ $implicit: T; value: unknown }>;
}

export interface SortState {
  key: string;
  dir: 'asc' | 'desc';
}

/**
 * DataTable — the workhorse list component. Sortable columns, optional row
 * selection with a bulk bar, numeric (right-aligned tabular) columns, hover
 * rows and a sticky header. Sorting is client-side. Column labels and the
 * selection/aria strings are inputs — pass already-translated values.
 * Mirrors `_ds_bundle.js` → DataTable.jsx.
 */
@Component({
  selector: 'hum-data-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, CheckboxComponent],
  host: { class: 'hum-table-wrap' },
  template: `
    @if (selectable() && selectedCount() > 0) {
      <div
        style="display:flex;align-items:center;gap:var(--space-3);padding:var(--space-2) var(--space-4);background:var(--brand-subtle);border-bottom:1px solid var(--brand-border);font-size:var(--text-sm);color:var(--brand-text);font-weight:var(--weight-medium)"
      >
        <span class="tabular-nums">{{ selectedCount() }} {{ selectedSuffix() }}</span>
        <div style="display:flex;gap:var(--space-2);margin-left:auto"><ng-content select="[hum-table-bulk]" /></div>
      </div>
    }
    <table class="hum-table">
      <thead>
        <tr>
          @if (selectable()) {
            <th style="width:36px">
              <hum-checkbox [checked]="allSelected()" [ariaLabel]="selectAllLabel()" (checkedChange)="toggleAll()" />
            </th>
          }
          @for (c of columns(); track c.key) {
            <th [class.hum-table__num]="c.numeric" [style.width]="c.width ?? null">
              @if (c.sortable) {
                <span
                  class="hum-table__sort"
                  role="button"
                  tabindex="0"
                  (click)="toggleSort(c)"
                  (keydown.enter)="toggleSort(c)"
                  (keydown.space)="toggleSort(c)"
                >
                  {{ c.label }}
                  <span aria-hidden="true" [style.opacity]="isSorted(c) ? 1 : 0.35" style="font-size:9px">{{ sortArrow(c) }}</span>
                </span>
              } @else {
                {{ c.label }}
              }
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @for (row of sorted(); track rowId(row)) {
          <tr
            [attr.data-selected]="isRowSelected(row) ? 'true' : null"
            [style.cursor]="clickableRows() ? 'pointer' : null"
            (click)="emitRowClick(row)"
          >
            @if (selectable()) {
              <td (click)="$event.stopPropagation()">
                <hum-checkbox [checked]="isRowSelected(row)" [ariaLabel]="selectRowLabel()" (checkedChange)="toggleRow(row)" />
              </td>
            }
            @for (c of columns(); track c.key) {
              <td [class.hum-table__num]="c.numeric">
                @if (c.cell) {
                  <ng-container [ngTemplateOutlet]="c.cell" [ngTemplateOutletContext]="{ $implicit: row, value: row[c.key] }" />
                } @else {
                  {{ row[c.key] }}
                }
              </td>
            }
          </tr>
        }
      </tbody>
    </table>
    @if (hasFooter()) {
      <div
        style="padding:var(--space-2_5) var(--space-4);border-top:1px solid var(--border-subtle);display:flex;align-items:center;justify-content:space-between;font-size:var(--text-sm);color:var(--text-muted)"
      >
        <ng-content select="[hum-table-footer]" />
      </div>
    }
  `,
})
export class DataTableComponent<T extends Row = Row> {
  readonly columns = input<Column<T>[]>([]);
  readonly rows = input<T[]>([]);
  readonly rowKey = input('id');
  readonly selectable = input(false, { transform: booleanAttribute });
  readonly initialSort = input<SortState | null>(null);
  /** Server-side sort: don't sort rows locally; emit `(sortChange)` for the caller to re-query. */
  readonly manualSort = input(false, { transform: booleanAttribute });
  readonly hasFooter = input(false, { transform: booleanAttribute });
  /** Show the pointer cursor + emit `rowClick` on row click. */
  readonly clickableRows = input(false, { transform: booleanAttribute });
  /** Accessible / display strings — pass translated values. */
  readonly selectedSuffix = input('selected');
  readonly selectAllLabel = input('Select all');
  readonly selectRowLabel = input('Select row');

  readonly rowClick = output<T>();
  readonly selectionChange = output<T[]>();
  readonly sortChange = output<SortState>();

  protected readonly effectiveSort = computed(() => this.userSort() ?? this.initialSort());
  protected readonly sorted = computed(() => {
    const sort = this.effectiveSort();
    const rows = this.rows();
    // Server-sorted rows arrive in order; don't re-sort locally.
    if (!sort || this.manualSort()) {
      return rows;
    }
    return [...rows].sort((a, b) => {
      const va = a[sort.key];
      const vb = b[sort.key];
      const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb));
      return sort.dir === 'asc' ? cmp : -cmp;
    });
  });
  protected readonly selectedCount = computed(() => this.selectedKeys().size);
  protected readonly allSelected = computed(() => this.rows().length > 0 && this.selectedKeys().size === this.rows().length);

  // null = "follow initialSort"; set once the user sorts a column.
  private readonly userSort = signal<SortState | null>(null);
  private readonly selectedKeys = signal<Set<unknown>>(new Set());

  protected rowId(row: T): unknown {
    return row[this.rowKey()];
  }

  protected isSorted(c: Column<T>): boolean {
    return this.effectiveSort()?.key === c.key;
  }

  protected sortArrow(c: Column<T>): string {
    const sort = this.effectiveSort();
    return sort?.key === c.key && sort.dir === 'desc' ? '▼' : '▲';
  }

  protected toggleSort(c: Column<T>): void {
    if (!c.sortable) {
      return;
    }
    const current = this.effectiveSort();
    const dir: 'asc' | 'desc' = current?.key === c.key && current.dir === 'asc' ? 'desc' : 'asc';
    const next = { key: c.key, dir };
    this.userSort.set(next);
    if (this.manualSort()) {
      this.sortChange.emit(next);
    }
  }

  protected isRowSelected(row: T): boolean {
    return this.selectedKeys().has(this.rowId(row));
  }

  protected toggleRow(row: T): void {
    const key = this.rowId(row);
    this.selectedKeys.update(set => {
      const next = new Set(set);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
    this.emitSelection();
  }

  protected toggleAll(): void {
    this.selectedKeys.set(this.allSelected() ? new Set() : new Set(this.rows().map(r => this.rowId(r))));
    this.emitSelection();
  }

  protected emitRowClick(row: T): void {
    if (this.clickableRows()) {
      this.rowClick.emit(row);
    }
  }

  private emitSelection(): void {
    const keys = this.selectedKeys();
    this.selectionChange.emit(this.rows().filter(r => keys.has(this.rowId(r))));
  }
}
