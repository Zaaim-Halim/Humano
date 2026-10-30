import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  DialogComponent,
  EmptyStateComponent,
  IconButtonComponent,
  InputComponent,
  SelectComponent,
  SkeletonComponent,
  ToastService,
} from 'app/shared/ui';

import { EmployeeDocument } from '../models/employee-document.model';
import { EmployeeDocumentService } from '../services/employee-document.service';

/** Suggested document types (free text on the backend). */
const DOCUMENT_TYPES = [
  'Employment contract',
  'Offer letter',
  'ID document',
  'Tax form',
  'Certificate',
  'Policy acknowledgement',
  'Medical certificate',
  'Other',
];
const ACCEPT = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 20 * 1024 * 1024;

/**
 * An employee's document library (`/employee-documents/employee/{id}`), shared by the employee
 * page and My documents.
 *
 * Documents show as cards — file-type icon, type, real file name, size and upload date — with
 * preview (PDFs and images open in a new tab), download and, for people who manage documents,
 * delete. Type chips and a search box filter; managers upload by dropping files (or browsing)
 * after picking a type. PDFs and images up to 20 MB, as the server enforces.
 */
@Component({
  selector: 'hum-document-library',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    DialogComponent,
    EmptyStateComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    SkeletonComponent,
  ],
  templateUrl: './document-library.component.html',
})
export class DocumentLibraryComponent {
  readonly employeeId = input.required<string>();
  /** Upload and delete (MANAGE_EMPLOYEE_DOCUMENTS). */
  readonly canManage = input(false);

  private readonly documentService = inject(EmployeeDocumentService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  protected readonly documents = signal<EmployeeDocument[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly typeFilter = signal('');
  protected readonly uploadType = signal(DOCUMENT_TYPES[0]);
  protected readonly uploading = signal(false);
  protected readonly dragging = signal(false);
  protected readonly deleteTarget = signal<EmployeeDocument | null>(null);
  protected readonly deleting = signal(false);
  protected readonly accept = ACCEPT.join(',');

  protected readonly typeOptions = DOCUMENT_TYPES.map(t => ({ value: t, label: t }));
  protected readonly typeChips = computed(() => {
    const counts = new Map<string, number>();
    for (const d of this.documents() ?? []) counts.set(d.type ?? '—', (counts.get(d.type ?? '—') ?? 0) + 1);
    return [...counts.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([type, count]) => ({ type, count }));
  });
  protected readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    const t = this.typeFilter();
    return (this.documents() ?? [])
      .filter(d => (!t || d.type === t) && (!q || `${d.type} ${d.fileName}`.toLowerCase().includes(q)))
      .sort((a, b) => (b.createdDate ?? '').localeCompare(a.createdDate ?? ''));
  });
  protected readonly totalSize = computed(() => (this.documents() ?? []).reduce((s, d) => s + (d.sizeBytes ?? 0), 0));

  constructor() {
    effect(() => {
      const id = this.employeeId();
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.employeeId()): void {
    this.error.set(null);
    this.documentService.forEmployee(id).subscribe({
      next: list => this.documents.set(list),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.documents.set([]);
      },
    });
  }

  protected kind(d: EmployeeDocument): 'pdf' | 'image' | 'file' {
    const type = d.contentType ?? '';
    const name = (d.fileName ?? '').toLowerCase();
    if (type === 'application/pdf' || name.endsWith('.pdf')) return 'pdf';
    if (type.startsWith('image/') || /\.(png|jpe?g|webp)$/.test(name)) return 'image';
    return 'file';
  }

  protected size(bytes: number | null): string {
    if (bytes === null || bytes === undefined) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  /** PDFs and images open in a new tab; anything else downloads. */
  protected preview(d: EmployeeDocument): void {
    if (this.kind(d) === 'file') {
      this.download(d);
      return;
    }
    const tab = this.document.defaultView?.open('', '_blank');
    this.documentService.download(d.id).subscribe({
      next(res) {
        if (!res.body) return;
        const url = URL.createObjectURL(new Blob([res.body], { type: d.contentType ?? res.body.type }));
        if (tab) tab.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err: unknown) => {
        tab?.close();
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected download(d: EmployeeDocument): void {
    this.documentService.download(d.id).subscribe({
      next: res => {
        if (!res.body) return;
        const url = URL.createObjectURL(res.body);
        const a = this.document.createElement('a');
        a.href = url;
        a.download = d.fileName ?? 'document';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  protected onPick(event: Event): void {
    const fileInput = event.target as HTMLInputElement;
    const files = [...(fileInput.files ?? [])];
    fileInput.value = '';
    this.upload(files);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.upload([...(event.dataTransfer?.files ?? [])]);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.documentService.delete(target.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.documents.update(list => (list ?? []).filter(d => d.id !== target.id));
        this.toast.success(this.t('humano.documents.deleted'));
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private upload(files: File[]): void {
    if (!this.canManage() || !files.length) return;
    const bad = files.find(f => !ACCEPT.includes(f.type) || f.size > MAX_BYTES);
    if (bad) {
      this.toast.danger(this.t('humano.documents.invalid', { name: bad.name }));
      return;
    }
    this.uploading.set(true);
    let pending = files.length;
    for (const file of files) {
      this.documentService.uploadForEmployee(this.employeeId(), file, { type: this.uploadType() }).subscribe({
        next: doc => {
          this.documents.update(list => [doc, ...(list ?? [])]);
          if (--pending === 0) {
            this.uploading.set(false);
            this.toast.success(this.t('humano.documents.uploaded', { count: files.length }));
          }
        },
        error: (err: unknown) => {
          if (--pending === 0) this.uploading.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
    }
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
