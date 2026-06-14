import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { Page, PageRequest } from './page.model';

/**
 * Building blocks for a REST resource, not a rigid CRUD contract. The backend
 * controllers deviate often (create at `POST /{userId}`, search at `POST
 * /search`, summary vs detail response shapes), so concrete services extend
 * this, reuse the verbs that fit, and declare their own non-standard calls.
 *
 * Type params: `TSummary` = list-row shape, `TDetail` = single-item shape
 * (default same), `TCreate`/`TUpdate` = request bodies.
 *
 * Services translate transport (HttpClient + interceptors) → typed Observables.
 * State/signals live in `createListResource`, not here.
 */
export abstract class RestResourceService<TSummary, TDetail = TSummary, TCreate = unknown, TUpdate = TCreate> {
  protected readonly http = inject(HttpClient);
  protected readonly appConfig = inject(ApplicationConfigService);
  protected readonly resourceUrl: string;

  /** @param path API path relative to the server prefix, e.g. `api/hr/employees`. */
  protected constructor(path: string) {
    this.resourceUrl = this.appConfig.getEndpointFor(path);
  }

  /** `GET {resource}` — paged list of summaries. */
  query(req?: PageRequest): Observable<Page<TSummary>> {
    return this.http.get<Page<TSummary>>(this.resourceUrl, { params: createRequestOption(req) });
  }

  /** `GET {resource}/{id}` — single detail. */
  find(id: string): Observable<TDetail> {
    return this.http.get<TDetail>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `POST {resource}` — create. */
  create(body: TCreate): Observable<TDetail> {
    return this.http.post<TDetail>(this.resourceUrl, body);
  }

  /** `PUT {resource}/{id}` — full update. */
  update(id: string, body: TUpdate): Observable<TDetail> {
    return this.http.put<TDetail>(`${this.resourceUrl}/${encodeURIComponent(id)}`, body);
  }

  /** `DELETE {resource}/{id}`. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }
}
