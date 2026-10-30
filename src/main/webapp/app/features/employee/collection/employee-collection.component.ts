import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { BadgeTone } from 'app/config/status-map';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DialogComponent,
  DrawerComponent,
  FormFieldComponent,
  IconButtonComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

export type CollectionFieldType = 'text' | 'email' | 'tel' | 'textarea' | 'number' | 'date' | 'checkbox' | 'select';

/** Column shown in the list. */
export interface CollectionColumn {
  key: string;
  label: string;
}

/** Form field shown in the add/edit drawer. `key` must match the request-body property. */
export interface CollectionField {
  key: string;
  label: string;
  type?: CollectionFieldType;
  options?: SelectOption[];
  required?: boolean;
}

/**
 * How one record is shown as a card: a title with badges, a subtitle, small icon facts, and an
 * aside (typically a date or range) on the right. Without a `present` function the card is built
 * from `columns` — first column as the title, the rest as facts.
 */
export interface RecordView {
  title: string;
  subtitle?: string | null;
  /** Leading icon; defaults to the section icon. */
  icon?: string;
  facts?: { icon: string; text: string | null | undefined }[];
  badges?: { label: string; tone: BadgeTone }[];
  aside?: string | null;
  /** Dims the card (e.g. a returned asset, an ended contract). */
  muted?: boolean;
}

export interface CollectionRecord {
  id: string;
  [key: string]: unknown;
}

/**
 * Structural contract every employee-owned RestResourceService already satisfies
 * (create/update/delete inherited; byEmployee added). Returns/bodies are intentionally loose so any
 * concrete typed service is assignable — the generic form produces a flat object keyed by field
 * that maps 1:1 to each entity's Create/Update request.
 */

export interface CollectionService {
  byEmployee(employeeId: string): Observable<any[]>;
  create(body: any): Observable<any>;
  update(id: string, body: any): Observable<any>;
  delete(id: string): Observable<void>;
}

/**
 * Generic CRUD manager for an employee-owned collection (addresses, certifications, …): a card
 * list with add/edit/delete driven by the `columns` + `fields` config (labels may be i18n keys)
 * and a `service`; `present` turns a record into a card. The drawer form is built dynamically
 * from `fields`; on submit the form value (plus `employeeId` on create) is the request body.
 */
@Component({
  selector: 'hum-employee-collection',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    BadgeComponent,
    ButtonComponent,
    IconButtonComponent,
    AlertComponent,
    SkeletonRowComponent,
    DrawerComponent,
    DialogComponent,
    InputComponent,
    TextareaComponent,
    SelectComponent,
    CheckboxComponent,
    FormFieldComponent,
  ],
  templateUrl: './employee-collection.component.html',
})
export class EmployeeCollectionComponent {
  readonly heading = input.required<string>();
  readonly employeeId = input.required<string>();
  readonly service = input.required<CollectionService>();
  readonly columns = input.required<CollectionColumn[]>();
  readonly fields = input.required<CollectionField[]>();
  readonly canManage = input(true);
  /** Section icon (header and default card icon). */
  readonly icon = input('folder');
  /** Singular name for "Add …" / "Edit …" (already translated); defaults to the heading. */
  readonly itemLabel = input<string | null>(null);
  readonly present = input<((row: any) => RecordView) | null>(null);
  /** Number of records, emitted after every load. */
  readonly count = output<number>();

  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  protected readonly rows = signal<CollectionRecord[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly editing = signal<CollectionRecord | null>(null);
  protected readonly deleteTarget = signal<CollectionRecord | null>(null);
  protected readonly deleting = signal(false);

  protected form = new FormGroup<Record<string, FormControl>>({});

  protected readonly views = computed(() => {
    const present = this.present();
    return this.rows().map(row => ({ row, view: present ? present(row) : this.fallbackView(row) }));
  });

  constructor() {
    effect(() => {
      const id = this.employeeId();
      if (id) {
        untracked(() => this.load());
      }
    });
  }

  protected cell(row: CollectionRecord, key: string): string {
    const v = row[key];
    if (v === null || v === undefined || v === '') return '—';
    if (typeof v === 'boolean') return v ? '✓' : '—';
    // eslint-disable-next-line @typescript-eslint/no-base-to-string -- record values are primitives from the API
    return String(v);
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service()
      .byEmployee(this.employeeId())
      .subscribe({
        next: data => {
          this.rows.set(data);
          this.loading.set(false);
          this.count.emit(data.length);
        },
        error: (e: unknown) => {
          this.error.set(normalizeHttpError(e));
          this.loading.set(false);
        },
      });
  }

  protected invalid(key: string): boolean {
    const c = this.form.get(key);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.buildForm(null);
    this.formOpen.set(true);
  }

  protected openEdit(row: CollectionRecord): void {
    this.editing.set(row);
    this.buildForm(row);
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: Record<string, unknown> = {};
    for (const f of this.fields()) {
      let v: unknown = raw[f.key];
      if (f.type === 'number') {
        v = v === '' || v === null ? undefined : Number(v);
      }
      if (v === '' || v === null) {
        continue; // omit empties so partial update leaves them unchanged
      }
      body[f.key] = v;
    }

    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.service().update(editing.id, body) : this.service().create({ ...body, employeeId: this.employeeId() });
    request$.subscribe({
      next: () => {
        this.toast.success(this.t(editing ? 'humano.records.updated' : 'humano.records.added'));
        this.formOpen.set(false);
        this.saving.set(false);
        this.load();
      },
      error: (e: unknown) => {
        this.toast.danger(normalizeHttpError(e));
        this.saving.set(false);
      },
    });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.service()
      .delete(target.id)
      .subscribe({
        next: () => {
          this.toast.success(this.t('humano.records.deleted'));
          this.deleting.set(false);
          this.deleteTarget.set(null);
          this.load();
        },
        error: (e: unknown) => {
          this.toast.danger(normalizeHttpError(e));
          this.deleting.set(false);
        },
      });
  }

  private fallbackView(row: CollectionRecord): RecordView {
    const [first, ...rest] = this.columns();
    return {
      title: first ? this.cell(row, first.key) : '—',
      facts: rest
        .filter(c => row[c.key] !== null && row[c.key] !== undefined && row[c.key] !== '')
        .map(c => ({ icon: 'dot', text: `${this.t(c.label)}: ${this.cell(row, c.key)}` })),
    };
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }

  private buildForm(record: CollectionRecord | null): void {
    const group: Record<string, FormControl> = {};
    for (const f of this.fields()) {
      const fallback = f.type === 'checkbox' ? false : '';
      const initial = record ? (record[f.key] ?? fallback) : fallback;
      group[f.key] = new FormControl(initial, { nonNullable: true, validators: f.required ? [Validators.required] : [] });
    }
    this.form = new FormGroup(group);
  }
}
