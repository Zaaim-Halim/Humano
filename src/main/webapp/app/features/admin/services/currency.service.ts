import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Currency } from '../models/currency.model';

/** Currency reference — `GET /api/payroll/currencies` (`@RequireAuthenticated`). */
@Injectable({ providedIn: 'root' })
export class CurrencyService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/currencies');

  /** `GET /api/payroll/currencies` — full reference list (bounded). */
  list(): Observable<Currency[]> {
    return this.http.get<Currency[]>(this.resourceUrl);
  }
}
