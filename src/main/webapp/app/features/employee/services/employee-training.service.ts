import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { EmployeeTraining } from '../models/employee-training.model';

/** Training enrolments — `/api/hr/employee-trainings`. */
@Injectable({ providedIn: 'root' })
export class EmployeeTrainingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/employee-trainings');

  /** `GET /api/hr/employee-trainings/search?employeeId=` — one employee's trainings (`VIEW_TRAINING`). */
  forEmployee(employeeId: string, req?: PageRequest): Observable<Page<EmployeeTraining>> {
    return this.http.get<Page<EmployeeTraining>>(`${this.resourceUrl}/search`, { params: createRequestOption({ employeeId, ...req }) });
  }
}
