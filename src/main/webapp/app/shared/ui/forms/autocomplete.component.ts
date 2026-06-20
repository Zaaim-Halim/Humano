import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  forwardRef,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

export interface AutocompleteOption {
  value: string;
  label: string;
  sublabel?: string | null;
}

let autocompleteUid = 0;

/**
 * Autocomplete — a single-select combobox whose options are fetched from the
 * backend as the user types. Use this instead of a `<hum-select>` whenever the
 * option set is unbounded or large (e.g. picking an employee/manager): pass a
 * `searchFn` that returns matches for a term, and the component debounces input,
 * switches to the latest request and renders the results.
 *
 * Implements ControlValueAccessor; the bound value is the selected option's
 * `value` (empty string when nothing is selected). In edit forms, pass
 * `initialOption` so the current value renders with its label before any search.
 */
@Component({
  selector: 'hum-autocomplete',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-ac', style: 'display: block; position: relative' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AutocompleteComponent), multi: true }],
  styles: [
    `
      .hum-ac__clear {
        position: absolute;
        top: 50%;
        right: var(--space-2);
        transform: translateY(-50%);
        border: none;
        background: transparent;
        color: var(--text-subtle);
        cursor: pointer;
        font-size: var(--text-lg);
        line-height: 1;
      }
      .hum-ac__panel {
        position: absolute;
        z-index: 20;
        top: calc(100% + 4px);
        left: 0;
        right: 0;
        max-height: 260px;
        overflow-y: auto;
        background: var(--bg-surface);
        border: 1px solid var(--border-default);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-md, 0 8px 24px rgb(0 0 0 / 12%));
        padding: var(--space-1);
      }
      .hum-ac__opt {
        display: flex;
        flex-direction: column;
        gap: 2px;
        width: 100%;
        text-align: left;
        border: none;
        background: transparent;
        padding: var(--space-2) var(--space-2_5);
        border-radius: var(--radius-sm);
        cursor: pointer;
        color: var(--text-default);
      }
      .hum-ac__opt:hover,
      .hum-ac__opt--active {
        background: var(--bg-muted);
      }
      .hum-ac__sub {
        font-size: var(--text-xs);
        color: var(--text-subtle);
      }
      .hum-ac__msg {
        padding: var(--space-2) var(--space-2_5);
        color: var(--text-subtle);
        font-size: var(--text-sm);
      }
    `,
  ],
  template: `
    <input
      class="hum-input"
      type="text"
      role="combobox"
      autocomplete="off"
      aria-autocomplete="list"
      [attr.aria-controls]="panelId"
      [value]="query()"
      [attr.placeholder]="placeholder()"
      [attr.aria-invalid]="invalid() ? 'true' : null"
      [attr.aria-expanded]="open()"
      [disabled]="isDisabled()"
      (input)="onInput($event)"
      (focus)="onFocus()"
      (blur)="onTouched()"
      (keydown)="onKeydown($event)"
    />
    @if (query() && !isDisabled()) {
      <button type="button" class="hum-ac__clear" [attr.aria-label]="clearLabel()" (mousedown)="clear(); $event.preventDefault()">×</button>
    }
    @if (open()) {
      <div class="hum-ac__panel" role="listbox" [id]="panelId">
        @if (loading()) {
          <div class="hum-ac__msg">{{ loadingText() }}</div>
        } @else if (results().length === 0) {
          <div class="hum-ac__msg">{{ noResultsText() }}</div>
        } @else {
          @for (o of results(); track o.value; let i = $index) {
            <button
              type="button"
              class="hum-ac__opt"
              role="option"
              [class.hum-ac__opt--active]="i === activeIndex()"
              [attr.aria-selected]="i === activeIndex()"
              (mousedown)="pick(o); $event.preventDefault()"
            >
              <span>{{ o.label }}</span>
              @if (o.sublabel) {
                <span class="hum-ac__sub">{{ o.sublabel }}</span>
              }
            </button>
          }
        }
      </div>
    }
  `,
})
export class AutocompleteComponent implements ControlValueAccessor {
  /** Returns matching options for a search term. Required. */
  readonly searchFn = input.required<(term: string) => Observable<AutocompleteOption[]>>();
  /** Seeds the displayed label for a pre-set value (edit forms). */
  readonly initialOption = input<AutocompleteOption | null>(null);
  readonly placeholder = input<string>();
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly minChars = input(1);
  readonly loadingText = input('Searching…');
  readonly noResultsText = input('No results');
  readonly clearLabel = input('Clear');

  protected readonly panelId = `hum-ac-panel-${++autocompleteUid}`;
  protected readonly query = signal('');
  protected readonly results = signal<AutocompleteOption[]>([]);
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly activeIndex = signal(-1);

  private readonly selected = signal<AutocompleteOption | null>(null);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());

  private readonly term$ = new Subject<string>();

  constructor() {
    this.term$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap(term => {
          if (term.trim().length < this.minChars()) {
            this.loading.set(false);
            return of<AutocompleteOption[]>([]);
          }
          this.loading.set(true);
          return this.searchFn()(term.trim()).pipe(catchError(() => of<AutocompleteOption[]>([])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe(opts => {
        this.results.set(opts);
        this.loading.set(false);
        this.activeIndex.set(-1);
      });

    // If a value was written before `initialOption` resolved, upgrade the label.
    effect(() => {
      const init = this.initialOption();
      const sel = untracked(this.selected);
      if (!init || !sel) return;
      if (sel.value === init.value && sel.label !== init.label) {
        this.selected.set(init);
        this.query.set(init.label);
      }
    });
  }

  writeValue(value: string | null): void {
    if (!value) {
      this.selected.set(null);
      this.query.set('');
      return;
    }
    const init = this.initialOption();
    const matched = init?.value === value ? init : null;
    this.selected.set(matched ?? { value, label: value });
    this.query.set(matched?.label ?? value);
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledByForm.set(isDisabled);
  }

  protected onChange: (value: string) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    // Editing the text invalidates any prior selection until the user re-picks.
    const sel = this.selected();
    if (sel && value !== sel.label) {
      this.selected.set(null);
      this.onChange('');
    }
    this.open.set(true);
    this.term$.next(value);
  }

  protected onFocus(): void {
    if (this.results().length || this.query()) this.open.set(true);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const items = this.results();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        if (items.length) this.activeIndex.set((this.activeIndex() + 1) % items.length);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (items.length) this.activeIndex.set((this.activeIndex() - 1 + items.length) % items.length);
        break;
      case 'Enter': {
        const idx = this.activeIndex();
        if (this.open() && idx >= 0 && idx < items.length) {
          event.preventDefault();
          this.pick(items[idx]);
        }
        break;
      }
      case 'Escape':
        this.open.set(false);
        break;
    }
  }

  protected pick(option: AutocompleteOption): void {
    this.selected.set(option);
    this.query.set(option.label);
    this.onChange(option.value);
    this.results.set([]);
    this.open.set(false);
  }

  protected clear(): void {
    this.selected.set(null);
    this.query.set('');
    this.results.set([]);
    this.open.set(false);
    this.onChange('');
  }
}
