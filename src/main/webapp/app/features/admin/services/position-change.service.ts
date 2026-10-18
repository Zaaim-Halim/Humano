import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { PositionChange, PositionChangeRequest } from '../models/position-change.model';

/** Someone's promotions, transfers and other position changes — `/api/hr/employees/{id}/position-changes`. */
@Injectable({ providedIn: 'root' })
export class PositionChangeService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/employees');

  /** The history, newest first (pay only for those who manage compensation). */
  history(employeeId: string): Observable<PositionChange[]> {
    return this.http.get<PositionChange[]>(`${this.resourceUrl}/${encodeURIComponent(employeeId)}/position-changes`);
  }

  /** Records a change from a date; returns its history entry. */
  change(employeeId: string, request: PositionChangeRequest): Observable<PositionChange> {
    return this.http.post<PositionChange>(`${this.resourceUrl}/${encodeURIComponent(employeeId)}/position-changes`, request);
  }
}
