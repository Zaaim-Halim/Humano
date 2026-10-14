import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent, PageHeaderComponent, SkeletonRowComponent } from 'app/shared/ui';

import { DocumentLibraryComponent } from '../documents/document-library.component';
import { CurrentEmployeeService } from '../services/current-employee.service';

/**
 * My documents — the signed-in employee's own document library (contracts, offer letter, tax
 * forms…), read-only: preview and download. HR adds and removes documents from the employee page.
 */
@Component({
  selector: 'hum-my-documents',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, PageHeaderComponent, AlertComponent, SkeletonRowComponent, DocumentLibraryComponent],
  templateUrl: './my-documents.component.html',
})
export default class MyDocumentsComponent {
  private readonly currentEmployee = inject(CurrentEmployeeService);

  protected readonly employeeId = this.currentEmployee.currentEmployeeId;
  protected readonly resolved = this.currentEmployee.resolved;

  constructor() {
    this.currentEmployee.resolve();
  }
}
