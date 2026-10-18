import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { Training } from '../models/training.model';

/** Training catalog — `/api/hr/trainings` (read side; part of the Training plan module). */
@Injectable({ providedIn: 'root' })
export class TrainingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/trainings');

  /** `GET /` — training programmes, paged. */
  list(req?: PageRequest): Observable<Page<Training>> {
    return this.http.get<Page<Training>>(this.resourceUrl, { params: createRequestOption(req) });
  }
}
