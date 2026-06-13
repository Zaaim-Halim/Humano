import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

export interface Command {
  id: string;
  label: string;
  /** Lucide icon name shown at the row start. */
  icon?: string;
  /** Right-aligned hint chip (e.g. a shortcut or section name). */
  hint?: string;
  /** Optional group heading; consecutive commands sharing a group are bucketed. */
  group?: string;
  /** Extra terms folded into the fuzzy match alongside label + group. */
  keywords?: string;
}

interface IndexedCommand extends Command {
  /** Position in the current flat (filtered) list — drives keyboard nav + aria. */
  _i: number;
}

/**
 * CommandPalette — the ⌘K (Ctrl+K) quick switcher. Standalone and decoupled
 * from `AppShell`: the consumer wires the shell's `(searchClick)` to
 * `openPalette()` and supplies the `commands` list (built from routes/nav).
 * Commands are data-driven inputs; selection emits `(run)` with the command id.
 *
 * Implements the WAI-ARIA combobox/listbox pattern: focus stays on the input,
 * arrow keys move `aria-activedescendant`, Enter runs, Escape closes. Focus is
 * captured on open and restored to the trigger on close.
 */
@Component({
  selector: 'hum-command-palette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  host: {
    style: 'display: contents',
    '(document:keydown)': 'onGlobalKey($event)',
  },
  template: `
    @if (open()) {
      <div class="hum-overlay hum-overlay--top" (click)="onOverlayClick($event)">
        <div class="hum-cmdk" role="dialog" aria-modal="true" [attr.aria-label]="ariaLabel()">
          <div class="hum-cmdk__search">
            <lucide-icon name="search" [size]="16" class="hum-cmdk__search-icon" aria-hidden="true" />
            <input
              #field
              type="text"
              class="hum-cmdk__input"
              role="combobox"
              aria-expanded="true"
              aria-controls="hum-cmdk-list"
              autocomplete="off"
              spellcheck="false"
              [attr.aria-activedescendant]="activeId()"
              [attr.aria-label]="ariaLabel()"
              [placeholder]="placeholder()"
              [value]="query()"
              (input)="query.set($any($event.target).value)"
              (keydown)="onKeydown($event)"
            />
            <kbd class="hum-kbd">Esc</kbd>
          </div>

          <div id="hum-cmdk-list" class="hum-cmdk__list" role="listbox" [attr.aria-label]="ariaLabel()">
            @for (group of groups(); track $index) {
              @if (group.heading; as heading) {
                <div class="hum-menu__label">{{ heading }}</div>
              }
              @for (cmd of group.items; track cmd.id) {
                <button
                  type="button"
                  role="option"
                  tabindex="-1"
                  [id]="'hum-cmdk-opt-' + cmd._i"
                  class="hum-menu__item"
                  [attr.aria-selected]="activeIndex() === cmd._i"
                  [attr.data-active]="activeIndex() === cmd._i ? 'true' : null"
                  (mousemove)="activeIndex.set(cmd._i)"
                  (click)="select(cmd)"
                >
                  @if (cmd.icon; as name) {
                    <span aria-hidden="true" style="display:inline-flex;color:var(--text-muted)">
                      <lucide-icon [name]="name" [size]="15" />
                    </span>
                  }
                  <span style="flex:1">{{ cmd.label }}</span>
                  @if (cmd.hint; as h) {
                    <kbd>{{ h }}</kbd>
                  }
                </button>
              }
            }
            @if (flat().length === 0) {
              <div class="hum-cmdk__empty">{{ emptyText() }}</div>
            }
          </div>
        </div>
      </div>
    }
  `,
})
export class CommandPaletteComponent {
  /** Two-way: bind `[(open)]` or drive via `openPalette()` / `close()`. */
  readonly open = model(false);
  readonly commands = input<Command[]>([]);
  readonly placeholder = input('Search commands…');
  readonly emptyText = input('No matching commands');
  readonly ariaLabel = input('Command palette');

  /** Emits the selected command id. */
  readonly run = output<string>();

  protected readonly query = signal('');
  private readonly field = viewChild<ElementRef<HTMLInputElement>>('field');
  private readonly document = inject(DOCUMENT);
  private previouslyFocused: HTMLElement | null = null;

  protected readonly flat = computed<IndexedCommand[]>(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.commands();
    const matched = q ? list.filter(c => `${c.label} ${c.keywords ?? ''} ${c.group ?? ''}`.toLowerCase().includes(q)) : list;
    return matched.map((c, i) => ({ ...c, _i: i }));
  });

  protected readonly groups = computed(() => {
    const out: { heading?: string; items: IndexedCommand[] }[] = [];
    for (const c of this.flat()) {
      const last = out[out.length - 1];
      if (last && last.heading === c.group) {
        last.items.push(c);
      } else {
        out.push({ heading: c.group, items: [c] });
      }
    }
    return out;
  });

  /** Highlighted row; resets to the top whenever the filtered set changes. */
  protected readonly activeIndex = linkedSignal<IndexedCommand[], number>({
    source: this.flat,
    computation: () => 0,
  });

  protected readonly activeId = computed(() => (this.flat().length ? `hum-cmdk-opt-${this.activeIndex()}` : null));

  constructor() {
    effect(() => {
      if (this.open()) {
        this.field()?.nativeElement.focus();
      }
    });

    // Keep the highlighted row visible when arrowing through a list taller than
    // the scroll viewport (the real case once route/nav commands are wired).
    effect(() => {
      const id = this.activeId();
      if (this.open() && id) {
        this.document.getElementById(id)?.scrollIntoView({ block: 'nearest' });
      }
    });
  }

  openPalette(): void {
    if (this.open()) return;
    this.previouslyFocused = this.document.activeElement as HTMLElement | null;
    this.query.set('');
    this.open.set(true);
  }

  close(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.query.set('');
    const prev = this.previouslyFocused;
    this.previouslyFocused = null;
    prev?.focus?.();
  }

  protected onGlobalKey(event: KeyboardEvent): void {
    if ((event.key === 'k' || event.key === 'K') && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      this.open() ? this.close() : this.openPalette();
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    const max = this.flat().length - 1;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (max >= 0) this.activeIndex.set(Math.min(this.activeIndex() + 1, max));
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (max >= 0) this.activeIndex.set(Math.max(this.activeIndex() - 1, 0));
        break;
      case 'Home':
        event.preventDefault();
        this.activeIndex.set(0);
        break;
      case 'End':
        event.preventDefault();
        if (max >= 0) this.activeIndex.set(max);
        break;
      case 'Enter': {
        event.preventDefault();
        const current = this.flat()[this.activeIndex()];
        if (current) this.select(current);
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      case 'Tab':
        // Combobox: keep focus on the input while open.
        event.preventDefault();
        break;
    }
  }

  protected select(cmd: Command): void {
    this.run.emit(cmd.id);
    this.close();
  }

  protected onOverlayClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }
}
