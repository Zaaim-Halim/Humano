import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Country } from '../models/country.model';

/** Country reference — `GET /api/hr/countries` (`@RequireAuthenticated`). */
@Injectable({ providedIn: 'root' })
export class CountryService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/countries');

  /** `GET /api/hr/countries` — full reference list, sorted by name. */
  list(): Observable<Country[]> {
    return this.http.get<Country[]>(this.resourceUrl);
  }
}
