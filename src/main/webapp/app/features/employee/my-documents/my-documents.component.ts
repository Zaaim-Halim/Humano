import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { EmployeeDocument, EmployeeDocumentService } from '../index';
import { CurrentEmployeeService } from '../services/current-employee.service';

/**
 * My documents (Employee self-service) — read-only list + download of the
 * signed-in user's own documents via `GET /api/hr/employee-documents/employee/{id}`
 * and `/download` (`@RequireHrStaffOrEmployee`).
 *
 * Like the rest of the self-service persona, this depends on resolving the
 * caller's own `employeeId`, for which no backend seam exists yet (see
 * {@link CurrentEmployeeService}). While unresolved the screen shows an honest
 * "not linked to a profile" notice rather than fetching someone else's PII; it
 * lights up automatically once that resolution lands. Upload/delete stay on the
 * HR-side employee-detail screen (gated `MANAGE_EMPLOYEE_DOCUMENTS`).
 */
@Component({
  selector: 'hum-my-documents',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, PageHeaderComponent, ButtonComponent, AlertComponent, EmptyStateComponent, SkeletonRowComponent],
  templateUrl: './my-documents.component.html',
})
export default class MyDocumentsComponent {
  private readonly documentService = inject(EmployeeDocumentService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  protected readonly employeeId = this.currentEmployee.currentEmployeeId;
  protected readonly resolved = this.currentEmployee.resolved;

  protected readonly documents = signal<EmployeeDocument[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.currentEmployee.resolve();
    // Load once a self id resolves; no-op while it stays null (see class doc).
    effect(() => {
      const id = this.employeeId();
      if (!id) return;
      untracked(() => this.load(id));
    });
  }

  protected retry(): void {
    const id = this.employeeId();
    if (id) this.load(id);
  }

  protected download(doc: EmployeeDocument): void {
    this.documentService.download(doc.id).subscribe({
      next: res => {
        const blob = res.body;
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = this.document.createElement('a');
        a.href = url;
        a.download = doc.filePath?.split('/').pop() ?? `${doc.type ?? 'document'}-${doc.id}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.documentService.forEmployee(id).subscribe({
      next: data => {
        this.documents.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }
}
