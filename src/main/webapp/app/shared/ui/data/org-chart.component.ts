import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Directive,
  ElementRef,
  TemplateRef,
  afterRenderEffect,
  computed,
  contentChild,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { Observable } from 'rxjs';

import { LucideAngularModule } from 'lucide-angular';

import { IconButtonComponent } from '../button/icon-button.component';

const MIN_ZOOM = 0.15;
const MAX_ZOOM = 2;
const STEP = 1.2; // multiplicative, so each click feels the same at any zoom
const FIT_PADDING = 48;
/**
 * Minimal shape of a chart node. `children` may be absent or trimmed (lazy hierarchies);
 * `childCount` then tells the chart a node has children it can fetch via `loadChildren`.
 */
export interface OrgChartNode {
  id: string;
  children?: readonly OrgChartNode[] | null;
  childCount?: number | null;
}

/** Context of the node / member templates: `let-node` and `let-depth="depth"`. */
export interface OrgChartNodeContext<T = any> {
  $implicit: T;
  depth: number;
}

/** Card content for a node: `<ng-template humOrgChartNode let-node let-depth="depth">…</ng-template>`. */
@Directive({ selector: 'ng-template[humOrgChartNode]' })
export class OrgChartNodeDirective {
  readonly template = inject<TemplateRef<OrgChartNodeContext>>(TemplateRef);
}

/**
 * Optional row for a leaf node inside a team panel. When provided, the children of a node who
 * have no children of their own are grouped into one panel instead of each getting a card.
 */
@Directive({ selector: 'ng-template[humOrgChartMember]' })
export class OrgChartMemberDirective {
  readonly template = inject<TemplateRef<OrgChartNodeContext>>(TemplateRef);
}

function childCountOf(node: OrgChartNode): number {
  return node.childCount ?? node.children?.length ?? 0;
}

function withoutKey(set: Set<string>, key: string): Set<string> {
  const next = new Set(set);
  next.delete(key);
  return next;
}

/** Elements that represent one searchable entry (a node card or a team-panel member). */
const SEARCHABLE = '.hum-orgnode, .hum-orgchart__member';

/**
 * OrgChart — a generic top-down hierarchy on a pannable, zoomable canvas. Renders any tree of
 * {@link OrgChartNode}s; the page supplies the card content (`humOrgChartNode` template) and,
 * optionally, a team-member row (`humOrgChartMember`) and a `loadChildren` loader so a
 * shallow tree can be fetched first and deeper branches on demand.
 *
 * - Expansion: nodes above `expandDepth` start expanded; per-node toggles override that until
 *   `expandDepth` changes. A node whose children were not loaded fetches them on first expand.
 *
 * - Toolbar (top-right, always visible): zoom out / level / zoom in, fit, 100%, full screen.
 * - The canvas grows with its content (no fixed height); full screen fills the screen.
 * - Ctrl/⌘ + wheel (and trackpad pinch) zooms toward the pointer; dragging the background pans.
 * - Keyboard when the canvas is focused: `+` / `-` zoom, `0` fit, `1` actual size.
 * - Whenever `roots` or `expandDepth` change the first root card is centred, so wide charts
 *   open on their top node instead of on empty canvas.
 * - Search box (top-left): matches node cards and team members by their text, dims the rest,
 *   and jumps between matches (Enter / Shift+Enter, Esc clears). `searchActive` fires when a
 *   query starts or clears so the page can expand collapsed branches while searching.
 *
 * Zoom uses CSS `zoom` (not `transform`) so scroll extents follow the scaled size.
 *
 * Usage:
 * ```html
 * <hum-org-chart [roots]="roots()" [expandDepth]="2" [loadChildren]="load" label="People tree">
 *   <ng-template humOrgChartNode let-node>{{ node.name }}</ng-template>
 * </hum-org-chart>
 * ```
 */
@Component({
  selector: 'hum-org-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconButtonComponent, LucideAngularModule, NgTemplateOutlet],
  template: `
    <div
      class="hum-orgchart"
      role="tree"
      tabindex="0"
      #viewport
      [attr.aria-label]="label()"
      [class.hum-orgchart--panning]="panning()"
      [class.hum-orgchart--searching]="query().length > 0"
      (wheel)="onWheel($event)"
      (pointerdown)="onPointerDown($event)"
      (pointermove)="onPointerMove($event)"
      (pointerup)="onPointerUp($event)"
      (pointercancel)="onPointerUp($event)"
      (keydown)="onKey($event)"
    >
      <div class="hum-orgchart__canvas" [style.zoom]="zoom()">
        <ng-container *ngTemplateOutlet="level; context: { $implicit: roots(), depth: 0 }" />
      </div>
    </div>

    <ng-template #level let-nodes let-depth="depth">
      <ul class="hum-orgchart__level" [attr.role]="depth === 0 ? null : 'group'" [style.gap]="depth === 0 ? 'var(--space-8)' : null">
        @for (n of branches(nodes, depth); track n.id) {
          <li
            class="hum-orgchart__item"
            role="treeitem"
            aria-selected="false"
            [attr.aria-expanded]="childCount(n) ? isExpanded(n, depth) : null"
          >
            <div class="hum-orgnode" [class.hum-orgnode--root]="depth === 0" [attr.data-tone]="nodeTone()?.(n) ?? null">
              @if (nodeDef(); as def) {
                <ng-container *ngTemplateOutlet="def.template; context: { $implicit: n, depth: depth }" />
              }
              @if (childCount(n)) {
                <button
                  type="button"
                  class="hum-orgnode__toggle"
                  [attr.aria-expanded]="isExpanded(n, depth)"
                  [disabled]="loading().has(n.id)"
                  (click)="toggle(n, depth)"
                >
                  {{ toggleLabel()(n) }}
                  <lucide-icon
                    [name]="loading().has(n.id) ? 'loader-circle' : isExpanded(n, depth) ? 'chevron-up' : 'chevron-down'"
                    [size]="12"
                    [class.hum-spin]="loading().has(n.id)"
                  />
                </button>
              }
            </div>
            @if (isExpanded(n, depth)) {
              <ng-container *ngTemplateOutlet="level; context: { $implicit: childrenOf(n), depth: depth + 1 }" />
            }
          </li>
        }
        @if (team(nodes, depth); as members) {
          @if (members.length && memberDef(); as def) {
            <li class="hum-orgchart__item">
              <ul class="hum-orgchart__team" role="group" [style.--hum-team-cols]="members.length > 6 ? 2 : 1">
                @for (m of members; track m.id) {
                  <li role="treeitem" aria-selected="false">
                    <ng-container *ngTemplateOutlet="def.template; context: { $implicit: m, depth: depth }" />
                  </li>
                }
              </ul>
            </li>
          }
        }
      </ul>
    </ng-template>
    @if (searchable()) {
      <div class="hum-orgchart__search" role="search">
        <lucide-icon name="search" [size]="14" aria-hidden="true" />
        <input
          type="search"
          [placeholder]="searchPlaceholder()"
          [attr.aria-label]="searchPlaceholder()"
          [value]="query()"
          (input)="onQuery($any($event.target).value)"
          (keydown)="onSearchKey($event)"
        />
        @if (query()) {
          <span class="hum-orgchart__search-count tabular-nums" aria-live="polite">
            {{ matchCount() ? current() + 1 + ' / ' + matchCount() : 'No matches' }}
          </span>
          <hum-icon-button icon="chevron-up" label="Previous match" size="sm" [disabled]="!matchCount()" (click)="goToMatch(-1)" />
          <hum-icon-button icon="chevron-down" label="Next match" size="sm" [disabled]="!matchCount()" (click)="goToMatch(1)" />
        }
      </div>
    }
    <div class="hum-orgchart__zoom" role="toolbar" aria-label="Chart view">
      <hum-icon-button icon="minus" label="Zoom out (−)" size="sm" [disabled]="zoom() <= minZoom" (click)="zoomBy(1 / step)" />
      <button type="button" class="hum-orgchart__zoom-value tabular-nums" title="Actual size (1)" (click)="setZoom(1)">
        {{ percent() }}%
      </button>
      <hum-icon-button icon="plus" label="Zoom in (+)" size="sm" [disabled]="zoom() >= maxZoom" (click)="zoomBy(step)" />
      <span class="hum-orgchart__zoom-sep" aria-hidden="true"></span>
      <hum-icon-button icon="scan" label="Fit to screen (0)" size="sm" (click)="fit()" />
      <hum-icon-button
        [icon]="fullscreen() ? 'minimize-2' : 'maximize-2'"
        [label]="fullscreen() ? 'Exit full screen' : 'Full screen'"
        size="sm"
        (click)="toggleFullscreen()"
      />
    </div>
    <p class="hum-orgchart__hint">Drag to move · Ctrl/⌘ + scroll to zoom</p>
  `,
  host: { class: 'hum-orgchart-host' },
})
export class OrgChartComponent {
  /** Accessible name of the tree. */
  readonly label = input('');
  /** Top-level nodes. */
  readonly roots = input<readonly OrgChartNode[]>([]);
  /** Levels expanded by default (roots are depth 0). Changing it resets per-node toggles. */
  readonly expandDepth = input(1);
  /** Fetches the children of a node whose `children` were not loaded (lazy hierarchies). */
  readonly loadChildren = input<((node: any) => Observable<readonly OrgChartNode[]>) | null>(null);
  /** Text of a node's expand toggle; defaults to its child count. */
  readonly toggleLabel = input<(node: any) => string>(n => String(childCountOf(n)));
  /** Optional accent tone per node (`brand` | `info` | `success` | `warning`). */
  readonly nodeTone = input<((node: any) => string | null) | null>(null);
  /** Show the search box; turn off when the page already filters the tree itself. */
  readonly searchable = input(true);
  /** Placeholder (and accessible name) of the search box. */
  readonly searchPlaceholder = input('Search');
  /** Emits true when a search starts and false when it is cleared. */
  readonly searchActive = output<boolean>();

  protected readonly nodeDef = contentChild(OrgChartNodeDirective);
  protected readonly memberDef = contentChild(OrgChartMemberDirective);
  /** Per-node expand overrides (id → expanded), cleared when `expandDepth` changes. */
  private readonly overrides = signal(new Map<string, boolean>());
  /** Children fetched lazily, by parent id. */
  private readonly loaded = signal(new Map<string, readonly OrgChartNode[]>());
  protected readonly loading = signal(new Set<string>());

  protected readonly query = signal('');
  protected readonly matchCount = signal(0);
  protected readonly current = signal(0);
  private matches: HTMLElement[] = [];

  protected readonly minZoom = MIN_ZOOM;
  protected readonly maxZoom = MAX_ZOOM;
  protected readonly step = STEP;
  protected readonly zoom = signal(1);
  protected readonly percent = computed(() => Math.round(this.zoom() * 100));
  protected readonly panning = signal(false);
  protected readonly fullscreen = signal(false);

  private readonly viewport = viewChild.required<ElementRef<HTMLElement>>('viewport');
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private drag: { id: number; x: number; y: number; left: number; top: number } | null = null;
  /** Scroll position to restore after a zoom, so the point under the pointer stays put. */
  private pendingScroll: { left: number; top: number } | null = null;

  constructor() {
    effect(() => {
      this.expandDepth();
      this.roots();
      untracked(() => this.overrides.set(new Map()));
    });
    afterRenderEffect(() => {
      this.roots();
      this.expandDepth();
      untracked(() => this.centerRoot());
    });
    afterRenderEffect(() => {
      // Re-run whenever the rendered tree changes: expanding branches renders new matches.
      const q = this.query().trim().toLowerCase();
      this.roots();
      this.overrides();
      this.loaded();
      this.expandDepth();
      untracked(() => this.applySearch(q));
    });
    afterRenderEffect(() => {
      this.zoom();
      untracked(() => {
        if (this.pendingScroll) {
          const el = this.viewport().nativeElement;
          el.scrollLeft = this.pendingScroll.left;
          el.scrollTop = this.pendingScroll.top;
          this.pendingScroll = null;
        }
      });
    });

    const onFullscreen = (): void => this.fullscreen.set(this.document.fullscreenElement === this.host.nativeElement);
    this.document.addEventListener('fullscreenchange', onFullscreen);
    inject(DestroyRef).onDestroy(() => this.document.removeEventListener('fullscreenchange', onFullscreen));
  }

  protected childCount(node: OrgChartNode): number {
    return childCountOf(node);
  }

  /** Children to render: lazily loaded ones first, then whatever came with the node. */
  protected childrenOf(node: OrgChartNode): readonly OrgChartNode[] {
    return this.loaded().get(node.id) ?? node.children ?? [];
  }

  /** Expanded only when its children are actually available to render. */
  protected isExpanded(node: OrgChartNode, depth: number): boolean {
    if (!this.childCount(node) || !this.childrenOf(node).length) return false;
    return this.overrides().get(node.id) ?? depth < this.expandDepth();
  }

  /** Nodes that get their own card: all roots, plus children who have children (or every child without a member template). */
  protected branches(nodes: readonly OrgChartNode[], depth: number): readonly OrgChartNode[] {
    return depth === 0 || !this.memberDef() ? nodes : nodes.filter(n => this.childCount(n) > 0);
  }

  /** Leaf children grouped into one team panel (only with a member template, below the roots). */
  protected team(nodes: readonly OrgChartNode[], depth: number): readonly OrgChartNode[] {
    return depth === 0 || !this.memberDef() ? [] : nodes.filter(n => this.childCount(n) === 0);
  }

  protected toggle(node: OrgChartNode, depth: number): void {
    const open = !this.isExpanded(node, depth);
    const loader = this.loadChildren();
    if (open && !this.childrenOf(node).length && loader) {
      this.loading.update(s => new Set(s).add(node.id));
      loader(node).subscribe({
        next: children => {
          this.loaded.update(m => new Map(m).set(node.id, children));
          this.setOverride(node.id, true);
        },
        complete: () => this.loading.update(s => withoutKey(s, node.id)),
        error: () => this.loading.update(s => withoutKey(s, node.id)),
      });
      return;
    }
    this.setOverride(node.id, open);
  }

  private setOverride(id: string, open: boolean): void {
    this.overrides.update(m => new Map(m).set(id, open));
  }

  /** Sets the zoom, keeping the given viewport point (default: the centre) fixed on screen. */
  protected setZoom(value: number, anchor?: { x: number; y: number }): void {
    const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value * 100) / 100));
    const prev = this.zoom();
    if (next === prev) return;
    const el = this.viewport().nativeElement;
    const ax = anchor?.x ?? el.clientWidth / 2;
    const ay = anchor?.y ?? el.clientHeight / 2;
    const ratio = next / prev;
    this.pendingScroll = { left: (el.scrollLeft + ax) * ratio - ax, top: (el.scrollTop + ay) * ratio - ay };
    this.zoom.set(next);
  }

  protected zoomBy(factor: number, anchor?: { x: number; y: number }): void {
    this.setZoom(this.zoom() * factor, anchor);
  }

  /**
   * Scales the chart so its full width fits the canvas (never above 100%) and centres it. The
   * canvas grows with its content, so height only constrains the fit in full screen.
   */
  protected fit(): void {
    const el = this.viewport().nativeElement;
    const size = this.contentSize();
    if (!size) return;
    const z = this.zoom();
    let next = (el.clientWidth - FIT_PADDING) / (size.w / z);
    if (this.fullscreen()) {
      next = Math.min(next, (el.clientHeight - FIT_PADDING) / (size.h / z));
    }
    this.zoom.set(Math.min(1, MAX_ZOOM, Math.max(MIN_ZOOM, Math.floor(next * 100) / 100)));
    this.pendingScroll = null;
    requestAnimationFrame(() => {
      el.scrollLeft = (el.scrollWidth - el.clientWidth) / 2;
      el.scrollTop = 0;
    });
  }

  protected async toggleFullscreen(): Promise<void> {
    if (this.document.fullscreenElement) {
      await this.document.exitFullscreen();
    } else {
      await this.host.nativeElement.requestFullscreen();
    }
  }

  protected onWheel(event: WheelEvent): void {
    // Plain wheel scrolls as usual; Ctrl/⌘ + wheel (and trackpad pinch, which sets ctrlKey) zooms.
    if (!event.ctrlKey && !event.metaKey) return;
    event.preventDefault();
    const rect = this.viewport().nativeElement.getBoundingClientRect();
    this.zoomBy(Math.exp(-event.deltaY * 0.0025), { x: event.clientX - rect.left, y: event.clientY - rect.top });
  }

  protected onPointerDown(event: PointerEvent): void {
    // Pan only from the background — links, buttons and team panels keep their normal behaviour.
    if (event.button !== 0 || (event.target as HTMLElement).closest('a, button, .hum-orgnode, .hum-orgchart__team')) return;
    const el = this.viewport().nativeElement;
    this.drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: el.scrollLeft, top: el.scrollTop };
    el.setPointerCapture(event.pointerId);
    this.panning.set(true);
  }

  protected onPointerMove(event: PointerEvent): void {
    if (this.drag?.id !== event.pointerId) return;
    const el = this.viewport().nativeElement;
    el.scrollLeft = this.drag.left - (event.clientX - this.drag.x);
    el.scrollTop = this.drag.top - (event.clientY - this.drag.y);
  }

  protected onPointerUp(event: PointerEvent): void {
    if (this.drag?.id !== event.pointerId) return;
    this.viewport().nativeElement.releasePointerCapture(event.pointerId);
    this.drag = null;
    this.panning.set(false);
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.target !== this.viewport().nativeElement) return;
    const actions: Partial<Record<string, () => void>> = {
      '+': () => this.zoomBy(STEP),
      '=': () => this.zoomBy(STEP),
      '-': () => this.zoomBy(1 / STEP),
      '0': () => this.fit(),
      '1': () => this.setZoom(1),
    };
    const action = actions[event.key];
    if (action && !event.ctrlKey && !event.metaKey) {
      event.preventDefault();
      action();
    }
  }

  protected onQuery(value: string): void {
    const wasActive = this.query().length > 0;
    this.query.set(value);
    this.current.set(0);
    if (wasActive !== value.length > 0) {
      this.searchActive.emit(value.length > 0);
    }
  }

  protected onSearchKey(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.goToMatch(event.shiftKey ? -1 : 1);
    } else if (event.key === 'Escape') {
      this.onQuery('');
    }
  }

  /** Moves to the next (+1) or previous (-1) match, wrapping around. */
  protected goToMatch(direction: 1 | -1): void {
    const n = this.matches.length;
    if (!n) return;
    this.current.set((this.current() + direction + n) % n);
    this.highlightCurrent();
  }

  private applySearch(q: string): void {
    const root = this.viewport().nativeElement;
    root.querySelectorAll('.is-match, .is-current').forEach(e => e.classList.remove('is-match', 'is-current'));
    this.matches = q ? Array.from(root.querySelectorAll<HTMLElement>(SEARCHABLE)).filter(e => e.textContent.toLowerCase().includes(q)) : [];
    this.matches.forEach(e => e.classList.add('is-match'));
    this.matchCount.set(this.matches.length);
    if (this.current() >= this.matches.length) this.current.set(0);
    this.highlightCurrent();
  }

  private highlightCurrent(): void {
    this.matches.forEach(e => e.classList.remove('is-current'));
    if (this.current() >= this.matches.length) return;
    const target = this.matches[this.current()];
    target.classList.add('is-current');
    const el = this.viewport().nativeElement;
    const box = target.getBoundingClientRect();
    const view = el.getBoundingClientRect();
    el.scrollTo({
      left: el.scrollLeft + box.left + box.width / 2 - (view.left + view.width / 2),
      top: el.scrollTop + box.top + box.height / 2 - (view.top + view.height / 2),
      behavior: 'smooth',
    });
  }

  /** Rendered (zoomed) size of the tree: the union of the top-level items' boxes. */
  private contentSize(): { w: number; h: number } | null {
    const items = this.viewport().nativeElement.querySelectorAll<HTMLElement>('.hum-orgchart__canvas > .hum-orgchart__level > li');
    if (!items.length) return null;
    let left = Infinity;
    let right = -Infinity;
    let top = Infinity;
    let bottom = -Infinity;
    items.forEach(li => {
      const r = li.getBoundingClientRect();
      left = Math.min(left, r.left);
      right = Math.max(right, r.right);
      top = Math.min(top, r.top);
      bottom = Math.max(bottom, r.bottom);
    });
    return { w: right - left, h: bottom - top };
  }

  private centerRoot(): void {
    const el = this.viewport().nativeElement;
    const root = el.querySelector<HTMLElement>('.hum-orgnode--root');
    if (!root) return;
    const box = root.getBoundingClientRect();
    const view = el.getBoundingClientRect();
    el.scrollLeft += box.left + box.width / 2 - (view.left + view.width / 2);
    el.scrollTop = 0;
  }
}
